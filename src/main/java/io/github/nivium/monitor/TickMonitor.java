package io.github.nivium.monitor;

import io.github.nivium.stats.GcEventLog;
import io.github.nivium.stats.JvmProbe;
import io.github.nivium.stats.TimingStats;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Continuous, low-overhead server tick monitor. All methods except {@link #snapshot()} run on the server
 * thread (Fabric tick and save events fire there).
 *
 * <p>Per tick: two {@code nanoTime()} reads and two ring-buffer writes. Once per second: a mean and one
 * 100-element sort. The 60 s statistics are refreshed every 5 s, or every 15 s while the server is overloaded,
 * so the monitor does less of its optional work exactly when the server can least afford it.
 */
public final class TickMonitor {
	public static final int TICKS_5S = 100;
	public static final int TICKS_10S = 200;
	public static final int TICKS_60S = 1200;
	/** Intervals longer than this are pauses (empty-server pause, debugger), not slow ticks. */
	private static final long PAUSE_NANOS = 5_000_000_000L;

	private final RollingTimes tickDurations = new RollingTimes(TICKS_60S);
	private final RollingTimes tickIntervals = new RollingTimes(TICKS_5S);
	private final SpikeLog spikes = new SpikeLog(64);
	private final GcEventLog gcLog;

	private final long anchorNanos = System.nanoTime();
	private final long anchorUptimeMs = JvmProbe.uptimeMillis();

	private double spikeThresholdMs;
	private long spikeThresholdNanos;

	private long tickStartNanos = -1;
	private long lastTickStartNanos = -1;
	private boolean savedThisTick;
	private int ticksUntilPublish = 20;

	private TimingStats last60s = TimingStats.EMPTY;
	/** -1 = never computed. (Not Long.MIN_VALUE: {@code now - MIN_VALUE} overflows to a negative number.) */
	private long last60sComputedNanos = -1;

	private volatile @Nullable PerformanceSnapshot snapshot;

	public TickMonitor(GcEventLog gcLog, double spikeThresholdMs) {
		this.gcLog = gcLog;
		this.setSpikeThresholdMs(spikeThresholdMs);
	}

	public void setSpikeThresholdMs(double ms) {
		this.spikeThresholdMs = ms;
		this.spikeThresholdNanos = (long) (ms * 1e6);
	}

	public void onTickStart() {
		long now = System.nanoTime();
		if (this.lastTickStartNanos >= 0) {
			long interval = now - this.lastTickStartNanos;
			if (interval < PAUSE_NANOS) {
				this.tickIntervals.add(interval);
			}
		}
		this.lastTickStartNanos = now;
		this.tickStartNanos = now;
		this.savedThisTick = false;
	}

	/** Fabric's BEFORE_SAVE: lets spikes caused by world saves (autosave, /save-all) be labelled. */
	public void onSave() {
		this.savedThisTick = true;
	}

	/** @return true once per second, when a new snapshot was published */
	public boolean onTickEnd(MinecraftServer server, AdaptiveStates adaptive) {
		if (this.tickStartNanos < 0) {
			return false;
		}
		long duration = System.nanoTime() - this.tickStartNanos;
		this.tickDurations.add(duration);
		if (duration > this.spikeThresholdNanos) {
			this.spikes.record(new SpikeLog.Spike(server.getTickCount(), this.uptimeAt(this.tickStartNanos), duration, this.savedThisTick));
		}
		if (--this.ticksUntilPublish > 0) {
			return false;
		}
		this.ticksUntilPublish = 20;
		this.publish(server, adaptive);
		return true;
	}

	private void publish(MinecraftServer server, AdaptiveStates adaptive) {
		long now = System.nanoTime();
		TimingStats last5s = TimingStats.compute(this.tickDurations.latest(TICKS_5S), 2.0, this.spikeThresholdMs);
		long refresh = last5s.meanMs() > 50 ? 15_000_000_000L : 5_000_000_000L;
		if (this.last60sComputedNanos < 0 || now - this.last60sComputedNanos >= refresh) {
			this.last60s = TimingStats.compute(this.tickDurations.latest(TICKS_60S), 2.0, this.spikeThresholdMs);
			this.last60sComputedNanos = now;
		}

		double targetTps = server.tickRateManager().tickrate();
		double meanIntervalMs = this.tickIntervals.meanMs(TICKS_5S);
		double tps = meanIntervalMs > 0 ? Math.min(targetTps, 1000.0 / meanIntervalMs) : targetTps;

		int chunks = 0;
		for (ServerLevel level : server.getAllLevels()) {
			chunks += level.getChunkSource().getLoadedChunksCount(); // a counter, not a world traversal
		}
		long[] gc = JvmProbe.gcTotals();

		this.snapshot = new PerformanceSnapshot(now, tps, targetTps, last5s, this.last60s, this.spikes.total(),
				this.spikeThresholdMs, JvmProbe.heapUsedBytes(), JvmProbe.heapMaxBytes(), gc[0], gc[1], chunks,
				server.getPlayerCount(), server.tickRateManager().isSprinting(), server.tickRateManager().isFrozen(),
				adaptive.simulation(), adaptive.view());
	}

	/** Mean milliseconds per tick over the last {@code ticks} ticks. */
	public double meanMspt(int ticks) {
		return this.tickDurations.meanMs(ticks);
	}

	public int recordedTicks() {
		return this.tickDurations.size();
	}

	public @Nullable PerformanceSnapshot snapshot() {
		return this.snapshot;
	}

	public SpikeLog spikes() {
		return this.spikes;
	}

	public GcEventLog gcLog() {
		return this.gcLog;
	}

	public long uptimeAt(long nanos) {
		return this.anchorUptimeMs + (nanos - this.anchorNanos) / 1_000_000L;
	}

	/** Supplies the adaptive-distance state for snapshots without the monitor depending on the optimizer. */
	public interface AdaptiveStates {
		PerformanceSnapshot.DistanceState simulation();

		PerformanceSnapshot.DistanceState view();
	}
}
