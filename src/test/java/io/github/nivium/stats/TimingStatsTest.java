package io.github.nivium.stats;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TimingStatsTest {
	private static final long MS = 1_000_000L;

	@Test
	void emptyInputYieldsZeros() {
		assertSame(TimingStats.EMPTY, TimingStats.compute(new long[0], 2, 50));
	}

	@Test
	void constantDurations() {
		long[] ticks = new long[1000];
		Arrays.fill(ticks, 10 * MS);
		TimingStats s = TimingStats.compute(ticks, 2, 50);
		assertEquals(10.0, s.meanMs(), 1e-9);
		assertEquals(10.0, s.p50Ms(), 1e-9);
		assertEquals(10.0, s.p99Ms(), 1e-9);
		assertEquals(0.0, s.stdDevMs(), 1e-9);
		assertEquals(0, s.spikes());
		assertEquals(10.0, s.totalSeconds(), 1e-9);
	}

	@Test
	void spikesUseTheLargerOfMultiplierAndFloor() {
		// 990 ticks at 10 ms, 10 at 120 ms. Median 10 ms -> 2x = 20 ms, floor 50 ms wins.
		long[] ticks = new long[1000];
		Arrays.fill(ticks, 10 * MS);
		for (int i = 0; i < 10; i++) {
			ticks[i * 100] = 120 * MS;
		}
		TimingStats s = TimingStats.compute(ticks, 2, 50);
		assertEquals(50.0, s.spikeThresholdMs(), 1e-9);
		assertEquals(10, s.spikes());
		assertEquals(120.0, s.maxMs(), 1e-9);
		// P99 rank = 0.99 * 999 = 989.01 -> between sorted[989]=10ms and sorted[990]=120ms.
		assertEquals(10.0 + 110.0 * 0.01, s.p99Ms(), 1e-6);
	}

	@Test
	void multiplierWinsUnderHeavyBaseLoad() {
		// Median 40 ms: 2x = 80 ms beats the 50 ms floor, so a 60 ms tick is not a spike.
		long[] ticks = new long[100];
		Arrays.fill(ticks, 40 * MS);
		ticks[3] = 60 * MS;
		ticks[4] = 90 * MS;
		TimingStats s = TimingStats.compute(ticks, 2, 50);
		assertEquals(80.0, s.spikeThresholdMs(), 1e-9);
		assertEquals(1, s.spikes());
	}

	@Test
	void percentileInterpolates() {
		long[] sorted = {0, 10, 20, 30, 40};
		assertEquals(20.0, TimingStats.percentile(sorted, 50), 1e-9);
		assertEquals(36.0, TimingStats.percentile(sorted, 90), 1e-9);
		assertEquals(40.0, TimingStats.percentile(sorted, 100), 1e-9);
		assertEquals(7.0, TimingStats.percentile(new long[]{7}, 99), 1e-9);
	}
}
