package io.github.nivium.monitor;

import io.github.nivium.stats.TimingStats;

/**
 * Immutable view of server performance, published once per second by the server thread. Safe to read
 * from any thread (the singleplayer client screen reads it).
 *
 * @param tps            ticks per second over the last 5 s, capped at the target rate
 * @param targetTps      the server's tick rate (20 unless changed with /tick rate)
 * @param last5s         tick-time statistics over the last 100 ticks
 * @param last60s        tick-time statistics over the last 1200 ticks (refreshed every 5-15 s)
 * @param totalSpikes    slow ticks since the server started
 * @param simulation     adaptive simulation-distance state
 * @param view           adaptive view-distance state
 */
public record PerformanceSnapshot(
		long createdNanos,
		double tps,
		double targetTps,
		TimingStats last5s,
		TimingStats last60s,
		long totalSpikes,
		double spikeThresholdMs,
		long heapUsedBytes,
		long heapMaxBytes,
		long gcCount,
		long gcTimeMs,
		int loadedChunks,
		int players,
		boolean sprinting,
		boolean frozen,
		DistanceState simulation,
		DistanceState view
) {
	/**
	 * @param active     whether Nivium is allowed to adjust this setting on this server
	 * @param current    the value in effect
	 * @param configured the admin's value (what Nivium restores)
	 */
	public record DistanceState(boolean active, int current, int configured) {
		public boolean reduced() {
			return this.current < this.configured;
		}
	}
}
