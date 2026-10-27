package io.github.nivium.client.lag;

import io.github.nivium.advice.ClientAdvice;
import io.github.nivium.client.hud.FrameMeter;
import io.github.nivium.client.perf.LowEndPreset;
import io.github.nivium.lag.LagVerdict;
import io.github.nivium.lag.ServerTpsEstimator;
import io.github.nivium.stats.GcEventLog;
import io.github.nivium.stats.JvmProbe;
import io.github.nivium.stats.StutterLog;
import io.github.nivium.stats.UptimeClock;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.List;

/**
 * Client-side lag check: combines FPS and stutters (this PC), estimated server TPS (from time packets) and ping
 * (connection). Everything is measured on the client, so it works on any server, modded or not. Render thread only.
 */
public final class LagMonitor {
	public static final GcEventLog GC = new GcEventLog(64);
	public static final StutterLog STUTTERS = new StutterLog(64);
	private static final ServerTpsEstimator TPS = new ServerTpsEstimator();
	private static final long MINUTE_MS = 60_000;
	private static final boolean SODIUM = FabricLoader.getInstance().isModLoaded("sodium");

	private LagMonitor() {
	}

	/** From the ClientPacketListener mixin, on the render thread. */
	public static void onTimePacket(long gameTime) {
		TPS.onTimeUpdate(System.nanoTime(), gameTime);
	}

	/** On joining or leaving a world/server: the TPS history belongs to the old one. */
	public static void reset() {
		TPS.reset();
	}

	public static double serverTps() {
		return TPS.tps();
	}

	public static double secondsSinceServerUpdate() {
		return TPS.secondsSinceUpdate(System.nanoTime());
	}

	public static int pingMs(Minecraft mc) {
		ClientPacketListener connection = mc.getConnection();
		if (connection == null || mc.player == null) {
			return -1;
		}
		PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
		return info != null ? info.getLatency() : -1;
	}

	/** Stutters of the last minute, newest first, each labeled GC or not. */
	public static List<StutterLog.Attributed> recentStutters() {
		return StutterLog.attribute(STUTTERS.since(UptimeClock.now() - MINUTE_MS), GC.recent());
	}

	public static LagVerdict.Result verdict(Minecraft mc) {
		List<StutterLog.Attributed> recent = recentStutters();
		int gc = (int) recent.stream().filter(StutterLog.Attributed::isGc).count();
		double target = mc.level != null ? mc.level.tickRateManager().tickrate() : 20;
		return LagVerdict.evaluate(new LagVerdict.Inputs(FrameMeter.fps(), recent.size(), gc, serverTps(), target,
				secondsSinceServerUpdate(), pingMs(mc), mc.hasSingleplayerServer()));
	}

	public static List<String> advice(Minecraft mc) {
		List<StutterLog.Attributed> recent = recentStutters();
		int gc = (int) recent.stream().filter(StutterLog.Attributed::isGc).count();
		return ClientAdvice.compute(new ClientAdvice.Inputs(JvmProbe.heapMaxBytes(), JvmProbe.totalPhysicalMemoryBytes(),
				JvmProbe.freePhysicalMemoryBytes(), mc.options.renderDistance().get(), FrameMeter.fps(),
				recent.size(), gc, SODIUM, LowEndPreset.isApplied()));
	}
}
