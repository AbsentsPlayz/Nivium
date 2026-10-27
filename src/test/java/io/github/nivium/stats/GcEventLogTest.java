package io.github.nivium.stats;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GcEventLogTest {
	private static GcEventLog.Event young(long startMs, long durationMs) {
		return new GcEventLog.Event(startMs, durationMs, "G1 Young Generation", "end of minor GC", "G1 Evacuation Pause");
	}

	@Test
	void keepsOnlyTheMostRecentEvents() {
		GcEventLog log = new GcEventLog(3);
		for (int i = 0; i < 5; i++) {
			log.add(young(i * 1000L, 10));
		}
		assertEquals(List.of(2000L, 3000L, 4000L), log.recent().stream().map(GcEventLog.Event::startUptimeMs).toList());
	}

	@Test
	void pauseOverlapCountsOnlyOverlappingPauses() {
		List<GcEventLog.Event> events = List.of(
				young(1_000, 50),     // 1000-1050: overlaps window start
				young(1_200, 300),    // 1200-1500: inside
				young(2_000, 10),     // after the window
				new GcEventLog.Event(1_100, 700, "G1 Concurrent GC", "end of concurrent GC pause", "G1 Remark"));
		assertEquals(350, GcEventLog.pauseMsOverlapping(events, 1_040, 1_600), "concurrent event excluded");
		assertEquals(0, GcEventLog.pauseMsOverlapping(events, 1_600, 1_900));
	}

	@Test
	void listenerRegistersAndUnregistersCleanly() {
		GcEventLog log = new GcEventLog(16);
		log.start();
		log.start(); // idempotent
		System.gc();
		log.stop();
		log.stop();
		// System.gc() may be disabled (-XX:+DisableExplicitGC), so the event count is not asserted.
		assertNotNull(log.recent());
	}
}
