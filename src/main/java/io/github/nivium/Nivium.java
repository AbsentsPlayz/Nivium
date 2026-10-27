package io.github.nivium;

import io.github.nivium.command.NiviumCommands;
import io.github.nivium.config.NiviumConfig;
import io.github.nivium.monitor.PerformanceSnapshot;
import io.github.nivium.monitor.SpikeLog;
import io.github.nivium.monitor.TickMonitor;
import io.github.nivium.optimize.AdaptiveDistances;
import io.github.nivium.stats.GcEventLog;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Server-side entrypoint. Runs on dedicated servers and on the integrated server of singleplayer/LAN worlds;
 * players never need Nivium installed.
 */
public final class Nivium implements ModInitializer {
	public static final String MOD_ID = "nivium";
	public static final Logger LOGGER = LoggerFactory.getLogger("Nivium");

	private static final GcEventLog GC_LOG = new GcEventLog(64);
	private static NiviumConfig config = new NiviumConfig();
	/** Volatile: the singleplayer client screen reads it from the render thread. */
	private static volatile @Nullable TickMonitor monitor;
	private static @Nullable AdaptiveDistances adaptive;

	private static long startNanos;
	private static long lastSpikeSummaryNanos;
	private static long spikesAtLastSummary;
	private static long lastStatusLogNanos;

	@Override
	public void onInitialize() {
		reloadConfig();

		ServerLifecycleEvents.SERVER_STARTING.register(Nivium::onServerStarting);
		ServerLifecycleEvents.SERVER_STOPPING.register(Nivium::onServerStopping);
		ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> {
			if (monitor != null) {
				monitor.onSave();
			}
		});
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			if (monitor != null) {
				monitor.onTickStart();
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Nivium::onTickEnd);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NiviumCommands.register(dispatcher));
	}

	public static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("nivium.json");
	}

	/** Loads config/nivium.json and applies it live. @return warnings to show the admin */
	public static List<String> reloadConfig() {
		NiviumConfig.LoadResult result = NiviumConfig.load(configPath());
		config = result.config();
		LOGGER.info("Config {} ({})", result.status(), configPath().getFileName());
		result.warnings().forEach(w -> LOGGER.warn("Config: {}", w));
		if (monitor != null) {
			monitor.setSpikeThresholdMs(config.monitoring.spikeThresholdMs);
		}
		return result.warnings();
	}

	/** Applies config changes (from reload or commands) to the running server. Server thread. */
	public static void applyConfig(MinecraftServer server) {
		if (monitor != null) {
			monitor.setSpikeThresholdMs(config.monitoring.spikeThresholdMs);
		}
		if (adaptive != null) {
			adaptive.applyConfig(config, server);
		}
	}

	private static void onServerStarting(MinecraftServer server) {
		GC_LOG.start();
		monitor = new TickMonitor(GC_LOG, config.monitoring.spikeThresholdMs);
		adaptive = new AdaptiveDistances(config);
		startNanos = System.nanoTime();
		lastSpikeSummaryNanos = startNanos;
		lastStatusLogNanos = startNanos;
		spikesAtLastSummary = 0;
		LOGGER.info("Monitoring server ticks ({} server)", server.isDedicatedServer() ? "dedicated" : "integrated");
	}

	private static void onServerStopping(MinecraftServer server) {
		if (adaptive != null) {
			adaptive.restoreAll(server);
		}
		GC_LOG.stop();
		monitor = null;
		adaptive = null;
	}

	private static void onTickEnd(MinecraftServer server) {
		TickMonitor m = monitor;
		AdaptiveDistances a = adaptive;
		if (m == null || a == null || !m.onTickEnd(server, a)) {
			return;
		}
		// Once per second from here on.
		long now = System.nanoTime();
		if (m.recordedTicks() >= TickMonitor.TICKS_10S) {
			a.evaluate(server, (now - startNanos) / 1e9, m.meanMspt(TickMonitor.TICKS_10S));
		}
		logSpikeSummary(m, now);
		logStatus(m, now);
	}

	/** At most one line per interval, and only when new spikes happened. */
	private static void logSpikeSummary(TickMonitor m, long now) {
		int interval = config.monitoring.spikeSummaryIntervalSeconds;
		if (interval <= 0 || now - lastSpikeSummaryNanos < interval * 1_000_000_000L) {
			return;
		}
		lastSpikeSummaryNanos = now;
		long total = m.spikes().total();
		long fresh = total - spikesAtLastSummary;
		spikesAtLastSummary = total;
		if (fresh <= 0) {
			return;
		}
		List<SpikeLog.Attributed> recent = SpikeLog.attribute(m.spikes().recent(), m.gcLog().recent());
		SpikeLog.Attributed worst = recent.stream()
				.limit(fresh)
				.max((x, y) -> Long.compare(x.spike().durationNanos(), y.spike().durationNanos()))
				.orElse(null);
		if (worst != null) {
			LOGGER.warn(String.format(Locale.ROOT, "%d slow tick(s) over %.0f ms in the last %d s; worst %.0f ms (%s). Details: /nivium spikes",
					fresh, config.monitoring.spikeThresholdMs, interval, worst.spike().durationMs(), worst.cause()));
		}
	}

	private static void logStatus(TickMonitor m, long now) {
		int minutes = config.monitoring.statusLogIntervalMinutes;
		PerformanceSnapshot s = m.snapshot();
		if (minutes <= 0 || s == null || now - lastStatusLogNanos < minutes * 60_000_000_000L) {
			return;
		}
		lastStatusLogNanos = now;
		LOGGER.info(String.format(Locale.ROOT, "TPS %.1f, MSPT mean %.1f / p95 %.1f / max %.0f (60 s), %d chunks, %d players",
				s.tps(), s.last60s().meanMs(), s.last60s().p95Ms(), s.last60s().maxMs(), s.loadedChunks(), s.players()));
	}

	public static NiviumConfig config() {
		return config;
	}

	public static @Nullable TickMonitor monitor() {
		return monitor;
	}

	public static @Nullable AdaptiveDistances adaptive() {
		return adaptive;
	}
}
