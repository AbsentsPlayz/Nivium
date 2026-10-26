package io.github.vortexoptimizer.bench;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class FrameStatsTest {
	private static final long MS = 1_000_000L;

	@Test
	void emptyInputYieldsZeros() {
		FrameStats s = FrameStats.compute(new long[0]);
		assertEquals(0, s.frames());
		assertEquals(0, s.averageFps());
		assertFalse(s.reliableLows());
	}

	@Test
	void constantFrameTimes() {
		long[] frames = new long[1000];
		Arrays.fill(frames, 10 * MS);
		FrameStats s = FrameStats.compute(frames);
		assertEquals(100.0, s.averageFps(), 1e-9);
		assertEquals(10.0, s.p50FrameMs(), 1e-9);
		assertEquals(10.0, s.p99FrameMs(), 1e-9);
		assertEquals(100.0, s.low1AverageFps(), 1e-9);
		assertEquals(0.0, s.stdDevFrameMs(), 1e-9);
		assertEquals(0, s.spikeFrames());
		assertEquals(10.0, s.totalSeconds(), 1e-9);
	}

	@Test
	void averageFpsIsFramesOverTimeNotMeanOfInstantFps() {
		// 1 frame of 100ms + 9 frames of 10ms = 190ms for 10 frames -> 52.63 FPS.
		// The mean of per-frame FPS would wrongly give (10 + 9*100)/10 = 91 FPS.
		long[] frames = new long[10];
		Arrays.fill(frames, 10 * MS);
		frames[3] = 100 * MS;
		FrameStats s = FrameStats.compute(frames);
		assertEquals(10 / 0.19, s.averageFps(), 1e-9);
	}

	@Test
	void onePercentLowsAndSpikes() {
		// 990 frames at 10ms, 10 frames at 50ms (the slowest 1%).
		long[] frames = new long[1000];
		Arrays.fill(frames, 10 * MS);
		for (int i = 0; i < 10; i++) {
			frames[i * 100] = 50 * MS;
		}
		FrameStats s = FrameStats.compute(frames);
		assertEquals(20.0, s.low1AverageFps(), 1e-9, "worst 1% = ten 50ms frames");
		assertEquals(20.0, s.low01AverageFps(), 1e-9, "worst 0.1% = one 50ms frame");
		assertEquals(10, s.spikeFrames());
		assertEquals(20.0, s.spikeThresholdMs(), 1e-9, "2 x median");
		assertEquals(50.0, s.maxFrameMs(), 1e-9);
		// P99 rank = 0.99 * 999 = 989.01 -> between sorted[989]=10ms and sorted[990]=50ms.
		assertEquals(10.0 + 40.0 * 0.01, s.p99FrameMs(), 1e-6);
	}

	@Test
	void spikeFloorPreventsFalseSpikesAtHighFps() {
		// 0.5ms median; a 1.5ms frame is 3x the median but below the 8ms absolute floor.
		long[] frames = new long[100];
		Arrays.fill(frames, MS / 2);
		frames[50] = 3 * MS / 2;
		assertEquals(0, FrameStats.compute(frames).spikeFrames());
	}

	@Test
	void percentileInterpolates() {
		long[] sorted = {0, 10, 20, 30, 40};
		assertEquals(20.0, FrameStats.percentile(sorted, 50), 1e-9);
		assertEquals(36.0, FrameStats.percentile(sorted, 90), 1e-9);
		assertEquals(40.0, FrameStats.percentile(sorted, 100), 1e-9);
		assertEquals(7.0, FrameStats.percentile(new long[]{7}, 99), 1e-9);
	}

	@Test
	void lowsReliabilityThreshold() {
		long[] frames = new long[FrameStats.MIN_FRAMES_FOR_LOWS];
		Arrays.fill(frames, 5 * MS);
		assertTrue(FrameStats.compute(frames).reliableLows());
		assertFalse(FrameStats.compute(Arrays.copyOf(frames, frames.length - 1)).reliableLows());
	}
}
