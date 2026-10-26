package io.github.vortexoptimizer.bench;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GcEventLogTest {
	private static final long MS = 1_000_000L;

	private static GcEventLog.Event at(long uptimeMs, long durationMs) {
		return new GcEventLog.Event(uptimeMs, durationMs, "G1 Young Generation", "end of minor GC", "G1 Evacuation Pause");
	}

	@Test
	void placesEventsInTheFrameThatContainsThem() {
		long[] frames = {10 * MS, 10 * MS, 100 * MS, 10 * MS}; // ends at 10, 20, 120, 130 ms
		long anchor = 5_000;
		List<GcEventLog.Placed> placed = GcEventLog.place(frames, anchor, List.of(
				at(5_000, 1),   // t=0      -> frame 0
				at(5_015, 1),   // t=15ms   -> frame 1
				at(5_020, 80),  // t=20ms, exactly a boundary -> frame 2
				at(5_125, 1)    // t=125ms  -> frame 3
		));
		assertEquals(List.of(0, 1, 2, 3), placed.stream().map(GcEventLog.Placed::frameIndex).toList());
		assertEquals(100.0, placed.get(2).frameMs(), 1e-9);
		assertEquals(0.020, placed.get(2).offsetSeconds(), 1e-9);
	}

	@Test
	void dropsEventsOutsideTheRecordedWindow() {
		long[] frames = {10 * MS, 10 * MS};
		assertTrue(GcEventLog.place(frames, 1_000, List.of(at(999, 5), at(1_020, 5), at(2_000, 5))).isEmpty());
		assertTrue(GcEventLog.place(new long[0], 0, List.of(at(0, 1))).isEmpty());
	}

	@Test
	void countsSpikeFramesWithGcOnce() {
		long[] frames = new long[100];
		java.util.Arrays.fill(frames, 10 * MS);
		frames[50] = 300 * MS;  // spike with two GC events in it
		frames[80] = 300 * MS;  // spike without GC
		long frame50Start = 50 * 10;
		List<GcEventLog.Placed> placed = GcEventLog.place(frames, 0,
				List.of(at(frame50Start + 1, 200), at(frame50Start + 100, 50), at(5, 3)));
		BenchmarkResult r = new BenchmarkResult("t", 0, 0, 1, FrameStats.compute(frames), false, -1, -1,
				Map.of(), placed, 0, 0, 0, 0, Map.of(), frames, new long[frames.length]);
		assertEquals(2, r.stats().spikeFrames());
		assertEquals(1, r.spikeFramesWithGcEvent());
	}

	@Test
	void listenerRegistersAndUnregistersCleanly() {
		GcEventLog log = new GcEventLog();
		log.start();
		System.gc();
		log.stop();
		log.stop(); // idempotent
		// System.gc() may be disabled (-XX:+DisableExplicitGC), so the event count is not asserted.
		assertNotNull(log.events());
	}
}
