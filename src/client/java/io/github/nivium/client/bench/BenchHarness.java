package io.github.nivium.client.bench;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.nivium.Nivium;
import io.github.nivium.client.cull.EntityOcclusionCuller;
import io.github.nivium.stats.JvmProbe;
import io.github.nivium.stats.TimingStats;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;

/**
 * Developer-only automated benchmark, used to measure Nivium's optimizations before and after. Inactive unless the
 * JVM is started with {@code -Dnivium.bench.seconds=N} (the {@code runBenchClient} Gradle task does that and
 * auto-joins a world). Players never see it.
 *
 * <p>Flow: wait until in a world, settle (chunks load, shaders compile), record N seconds of frame times plus
 * render-thread allocation, GC and process CPU, write {@code nivium-bench/<label>.json}, quit the game.
 * Focus pause and the idle FPS limiter are disabled for the run (and restored before quitting) so a background
 * window does not distort the numbers.
 */
public final class BenchHarness {
	private static final long SECONDS = Long.getLong("nivium.bench.seconds", 0);
	private static final long SETTLE_SECONDS = Long.getLong("nivium.bench.settle", 25);
	private static final String LABEL = System.getProperty("nivium.bench.label", "run");
	private static final String SCENE = System.getProperty("nivium.bench.scene", "none");
	/** Constant, so the per-frame hook compiles away entirely when benchmarking is off. */
	public static final boolean ENABLED = SECONDS > 0;

	private enum State { WAITING, SETTLING, RECORDING, DONE }

	private static State state = State.WAITING;
	private static long phaseStart;
	private static long[] frames = new long[0];
	private static int count;
	private static long lastFrame = -1;
	private static long allocStart;
	private static long cpuStart;
	private static long[] gcStart;
	private static long consideredStart;
	private static long exemptStart;
	private static long rayTestsStart;
	private static long cacheHitsStart;
	private static long culledStart;
	private static boolean savedPauseOnLostFocus;
	private static InactivityFpsLimit savedInactivity;

	private BenchHarness() {
	}

	/** Render thread, once per frame (from FrameMeter). */
	public static void onFrame(long now) {
		if (state != State.RECORDING) {
			lastFrame = now;
			return;
		}
		if (lastFrame >= 0 && count < frames.length) {
			frames[count++] = now - lastFrame;
		}
		lastFrame = now;
		if (now - phaseStart >= SECONDS * 1_000_000_000L) {
			finish(now);
		}
	}

	/** Client thread, end of each client tick. */
	public static void onClientTick(Minecraft mc) {
		long now = System.nanoTime();
		switch (state) {
			case WAITING -> {
				if (mc.level != null && mc.player != null && mc.screen == null) {
					savedPauseOnLostFocus = mc.options.pauseOnLostFocus;
					savedInactivity = mc.options.inactivityFpsLimit().get();
					mc.options.pauseOnLostFocus = false;
					mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
					setUpScene(mc);
					state = State.SETTLING;
					phaseStart = now;
					Nivium.LOGGER.info("[bench] in world; settling {} s", SETTLE_SECONDS);
				}
			}
			case SETTLING -> {
				if (now - phaseStart >= SETTLE_SECONDS * 1_000_000_000L) {
					allocStart = renderThreadAllocated();
					cpuStart = JvmProbe.processCpuNanos();
					gcStart = JvmProbe.gcTotals();
					consideredStart = EntityOcclusionCuller.considered;
					exemptStart = EntityOcclusionCuller.exempt;
					rayTestsStart = EntityOcclusionCuller.rayTests;
					Nivium.LOGGER.info("[bench] client entities: {}", mc.level.getEntityCount());
					cacheHitsStart = EntityOcclusionCuller.cacheHits;
					culledStart = EntityOcclusionCuller.culled;
					frames = new long[1 << 20];
					count = 0;
					lastFrame = -1;
					phaseStart = now;
					state = State.RECORDING;
					Nivium.LOGGER.info("[bench] recording {} s as '{}'", SECONDS, LABEL);
				}
			}
			default -> {
			}
		}
	}

	/**
	 * Builds a repeatable entity workload in the (freshly restored) benchmark world via the integrated server:
	 * the camera faces +Z over a cleared area holding 180 frozen cows (no AI, no gravity). Scene "occluded" adds a
	 * stone wall between camera and cows that fills the view; "visible" leaves them in plain sight.
	 */
	private static void setUpScene(Minecraft mc) {
		if ("none".equals(SCENE) || mc.getSingleplayerServer() == null) {
			return;
		}
		int x = mc.player.getBlockX();
		int y = mc.player.getBlockY();
		int z = mc.player.getBlockZ();
		java.util.List<String> cmds = new java.util.ArrayList<>();
		cmds.add("gamemode creative @a");
		cmds.add(String.format("tp @a %d.5 %d %d.5 0 0", x, y, z));
		cmds.add(String.format("fill %d %d %d %d %d %d air", x - 12, y, z + 2, x + 12, y + 7, z + 24));
		cmds.add(String.format("fill %d %d %d %d %d %d stone", x - 12, y - 1, z + 2, x + 12, y - 1, z + 24));
		if ("occluded".equals(SCENE)) {
			cmds.add(String.format("fill %d %d %d %d %d %d stone", x - 12, y, z + 5, x + 12, y + 7, z + 5));
		}
		for (int dz = 10; dz <= 19; dz += 3) {
			for (int i = 0; i < 15; i++) {
				for (int dy = 0; dy <= 4; dy += 2) {
					cmds.add(String.format(java.util.Locale.ROOT,
							"summon minecraft:cow %.1f %d %d.5 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b,NoGravity:1b}",
							x - 10.0 + i * 1.5, y + dy, z + dz));
				}
			}
		}
		var server = mc.getSingleplayerServer();
		server.execute(() -> {
			var source = server.createCommandSourceStack().withSuppressedOutput();
			for (String c : cmds) {
				server.getCommands().performPrefixedCommand(source, c);
			}
		});
		Nivium.LOGGER.info("[bench] scene '{}' set up with {} commands", SCENE, cmds.size());
	}

	private static long renderThreadAllocated() {
		return ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean t
				? t.getCurrentThreadAllocatedBytes() : -1;
	}

	private static void finish(long now) {
		state = State.DONE;
		double seconds = (now - phaseStart) / 1e9;
		long alloc = renderThreadAllocated() - allocStart;
		long cpu = JvmProbe.processCpuNanos() - cpuStart;
		long[] gcEnd = JvmProbe.gcTotals();
		long[] recorded = Arrays.copyOf(frames, count);
		TimingStats s = TimingStats.compute(recorded, 2.0, 50);

		long[] sorted = recorded.clone();
		Arrays.sort(sorted);
		int worst = Math.max(1, count / 100);
		double worstSum = 0;
		for (int i = count - worst; i < count; i++) {
			worstSum += sorted[i];
		}

		JsonObject o = new JsonObject();
		o.addProperty("label", LABEL);
		o.addProperty("scene", SCENE);
		o.addProperty("time", Instant.now().toString());
		o.addProperty("seconds", seconds);
		o.addProperty("frames", count);
		o.addProperty("avgFps", count / seconds);
		o.addProperty("low1AvgFps", worst / (worstSum / 1e9));
		o.addProperty("p50Ms", s.p50Ms());
		o.addProperty("p95Ms", s.p95Ms());
		o.addProperty("p99Ms", s.p99Ms());
		o.addProperty("maxMs", s.maxMs());
		o.addProperty("framesOver50Ms", s.spikes());
		o.addProperty("renderThreadAllocMiBPerSec", alloc / 1048576.0 / seconds);
		o.addProperty("renderThreadAllocBytesPerFrame", count > 0 ? (double) alloc / count : 0);
		o.addProperty("gcCount", gcEnd[0] - gcStart[0]);
		o.addProperty("gcTimeMs", gcEnd[1] - gcStart[1]);
		o.addProperty("processCpuCores", cpu / 1e9 / seconds);
		o.addProperty("entitiesConsideredPerFrame", count > 0 ? (double) (EntityOcclusionCuller.considered - consideredStart) / count : 0);
		o.addProperty("entitiesExemptPerFrame", count > 0 ? (double) (EntityOcclusionCuller.exempt - exemptStart) / count : 0);
		o.addProperty("entitiesCulledPerFrame", count > 0 ? (double) (EntityOcclusionCuller.culled - culledStart) / count : 0);
		o.addProperty("cullRayTestsPerFrame", count > 0 ? (double) (EntityOcclusionCuller.rayTests - rayTestsStart) / count : 0);
		o.addProperty("cullCacheHitsPerFrame", count > 0 ? (double) (EntityOcclusionCuller.cacheHits - cacheHitsStart) / count : 0);
		o.addProperty("jvmArgs", String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
				.filter(a -> a.startsWith("-Dnivium") || a.startsWith("-Xm")).toList()));

		Minecraft mc = Minecraft.getInstance();
		try {
			Path dir = FabricLoader.getInstance().getGameDir().resolve("nivium-bench");
			Files.createDirectories(dir);
			Path file = dir.resolve(LABEL + ".json");
			Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(o), StandardCharsets.UTF_8);
			Nivium.LOGGER.info("[bench] wrote {}: {}", file, o);
		} catch (Exception e) {
			Nivium.LOGGER.error("[bench] could not write result", e);
		}
		mc.options.pauseOnLostFocus = savedPauseOnLostFocus;
		mc.options.inactivityFpsLimit().set(savedInactivity);
		frames = new long[0];
		mc.stop();
	}
}
