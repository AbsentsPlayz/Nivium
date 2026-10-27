package io.github.nivium.stats;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StutterLogTest {
	private static final long MS = 1_000_000L;

	@Test
	void boundedButCountsEverything() {
		StutterLog log = new StutterLog(2);
		for (int i = 0; i < 5; i++) {
			log.record(new StutterLog.Stutter(i * 1000L, 200 * MS));
		}
		assertEquals(5, log.total());
		assertEquals(List.of(3000L, 4000L), log.recent().stream().map(StutterLog.Stutter::startUptimeMs).toList());
		assertEquals(1, log.since(3500).size());
	}

	@Test
	void stuttersAreAttributedNewestFirst() {
		List<StutterLog.Stutter> stutters = List.of(
				new StutterLog.Stutter(10_000, 400 * MS),  // GC 350 ms inside -> GC
				new StutterLog.Stutter(30_000, 500 * MS),  // GC 100 ms -> partly
				new StutterLog.Stutter(40_000, 200 * MS)); // only a concurrent event -> not GC
		List<GcEventLog.Event> gc = List.of(
				new GcEventLog.Event(10_020, 350, "G1 Young Generation", "end of minor GC", "G1 Evacuation Pause"),
				new GcEventLog.Event(30_100, 100, "G1 Young Generation", "end of minor GC", "G1 Evacuation Pause"),
				new GcEventLog.Event(40_000, 700, "G1 Concurrent GC", "end of concurrent GC pause", "G1 Remark"));
		List<StutterLog.Attributed> a = StutterLog.attribute(stutters, gc);
		assertEquals(List.of(40_000L, 30_000L, 10_000L), a.stream().map(x -> x.stutter().startUptimeMs()).toList());
		assertFalse(a.get(0).isGc());
		assertEquals("partly memory cleanup (GC 100 ms)", a.get(1).cause());
		assertEquals("memory cleanup (GC pause 350 ms)", a.get(2).cause());
	}
}
