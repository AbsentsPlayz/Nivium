package io.github.vortexoptimizer.client.bench;

import io.github.vortexoptimizer.VortexOptimizer;
import io.github.vortexoptimizer.bench.BenchmarkResult;
import io.github.vortexoptimizer.bench.BenchmarkSession;
import io.github.vortexoptimizer.bench.FrameStats;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Owns the active benchmark session. All methods are called on the render thread; only the
 * report write is moved to a background thread so serializing ~250k CSV rows never lands in a frame.
 */
public final class BenchmarkController {
	private static @Nullable BenchmarkSession active;
	private static @Nullable Map<String, Object> startEnvironment;
	private static double durationSeconds;
	private static @Nullable String lastSummary;

	private BenchmarkController() {
	}

	/** Mixin hook: called once at the start of every client frame. Must stay cheap when idle. */
	public static void onFrameStart() {
		BenchmarkSession session = active;
		if (session != null && !session.onFrameBoundary(System.nanoTime())) {
			complete(session);
		}
	}

	public static boolean isRunning() {
		return active != null;
	}

	public static @Nullable BenchmarkSession active() {
		return active;
	}

	/** Requested duration of the active run; 0 when it runs until stopped. */
	public static double durationSeconds() {
		return durationSeconds;
	}

	/** One-line summary of the last finished run in this game session, or null. */
	public static @Nullable String lastSummary() {
		return lastSummary;
	}

	public static boolean start(String label, double seconds, int warmupFrames) {
		if (active != null) {
			return false;
		}
		durationSeconds = seconds;
		startEnvironment = EnvironmentCollector.collect(Minecraft.getInstance());
		active = new BenchmarkSession(label, (long) (seconds * 1e9), warmupFrames, BenchmarkSession.DEFAULT_CAPACITY);
		VortexOptimizer.LOGGER.info("Benchmark '{}' started ({}s, {} warm-up frames)", label, seconds, warmupFrames);
		return true;
	}

	public static boolean stop() {
		BenchmarkSession session = active;
		if (session == null) {
			return false;
		}
		session.finish(System.nanoTime());
		complete(session);
		return true;
	}

	/** Abandons a running session without writing a report (e.g. on disconnect). */
	public static void cancel() {
		if (active != null) {
			active.finish(System.nanoTime()); // releases the GC listeners
			VortexOptimizer.LOGGER.info("Benchmark '{}' cancelled", active.label());
		}
		active = null;
		startEnvironment = null;
	}

	private static void complete(BenchmarkSession session) {
		active = null;
		Map<String, Object> env = startEnvironment != null ? startEnvironment : Map.of();
		startEnvironment = null;
		Map<String, Object> endEnv = EnvironmentCollector.collect(Minecraft.getInstance());
		if (!EnvironmentCollector.settingsEqual(env, endEnv)) {
			env = new java.util.LinkedHashMap<>(env);
			env.put("warning", "Video settings or window size changed during the run; results are not comparable.");
			env.put("endSettings", endEnv);
		}
		BenchmarkResult result = session.result(env);

		Path dir = reportsDir();
		CompletableFuture.supplyAsync(() -> {
			try {
				return result.write(dir);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}).whenComplete((path, error) -> Minecraft.getInstance().execute(() -> {
			if (error != null) {
				VortexOptimizer.LOGGER.error("Failed to write benchmark report", error);
				chat(Component.literal("[Vortex] Failed to write benchmark report: " + error.getMessage()));
			} else {
				VortexOptimizer.LOGGER.info("Benchmark report written to {}", path);
				chat(Component.literal("[Vortex] Report: " + dir.relativize(path)));
			}
		}));

		String summary = describe(result);
		lastSummary = session.label() + ": " + summary;
		VortexOptimizer.LOGGER.info("Benchmark '{}' finished: {}", session.label(), summary);
		chat(Component.literal("[Vortex] " + summary));
	}

	public static Path reportsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("vortex-benchmarks");
	}

	static String describe(BenchmarkResult r) {
		FrameStats s = r.stats();
		String lows = s.reliableLows() ? "" : " (lows unreliable: <" + FrameStats.MIN_FRAMES_FOR_LOWS + " frames)";
		String alloc = r.renderThreadAllocatedBytes() >= 0
				? String.format(Locale.ROOT, ", alloc %.1f MiB/s", r.renderThreadAllocatedBytes() / 1048576.0 / Math.max(r.wallSeconds(), 1e-9))
				: "";
		return String.format(Locale.ROOT,
				"%d frames in %.1fs: avg %.1f FPS, 1%% low %.1f FPS (avg) / %.1f FPS (P99), p50 %.2fms, max %.1fms, %d spikes (%d with GC)%s%s",
				s.frames(), r.wallSeconds(), s.averageFps(), s.low1AverageFps(), s.low1PercentileFps(),
				s.p50FrameMs(), s.maxFrameMs(), s.spikeFrames(), r.spikeFramesWithGcEvent(), alloc, lows);
	}

	private static void chat(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(message, false);
		}
	}
}
