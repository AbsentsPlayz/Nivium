package io.github.nivium.monitor;

import io.github.nivium.stats.GcEventLog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MonitorPartsTest {
	private static final long MS = 1_000_000L;

	@Test
	void rollingTimesWrapsAndReturnsOldestFirst() {
		RollingTimes r = new RollingTimes(4);
		assertEquals(0, r.meanMs(10));
		assertArrayEquals(new long[0], r.latest(3));
		for (int i = 1; i <= 6; i++) {
			r.add(i * MS);
		}
		assertEquals(4, r.size());
		assertArrayEquals(new long[]{3 * MS, 4 * MS, 5 * MS, 6 * MS}, r.latest(10));
		assertArrayEquals(new long[]{5 * MS, 6 * MS}, r.latest(2));
		assertEquals(5.5, r.meanMs(2), 1e-9);
		assertEquals(4.5, r.meanMs(100), 1e-9);
		r.clear();
		assertEquals(0, r.size());
	}

	@Test
	void spikeLogIsBoundedButCountsEverything() {
		SpikeLog log = new SpikeLog(2);
		for (int i = 0; i < 5; i++) {
			log.record(new SpikeLog.Spike(i, i * 1000L, 200 * MS, false));
		}
		assertEquals(5, log.total());
		assertEquals(List.of(3, 4), log.recent().stream().map(SpikeLog.Spike::tick).toList());
	}

	@Test
	void spikesAreAttributedNewestFirst() {
		List<SpikeLog.Spike> spikes = List.of(
				new SpikeLog.Spike(1, 10_000, 400 * MS, false),  // 10000-10400, GC 350 ms inside -> GC
				new SpikeLog.Spike(2, 20_000, 300 * MS, true),   // during save
				new SpikeLog.Spike(3, 30_000, 500 * MS, false),  // GC 100 ms -> partly
				new SpikeLog.Spike(4, 40_000, 200 * MS, false)); // nothing
		List<GcEventLog.Event> gc = List.of(
				new GcEventLog.Event(10_020, 350, "G1 Young Generation", "end of minor GC", "G1 Evacuation Pause"),
				new GcEventLog.Event(30_100, 100, "G1 Young Generation", "end of minor GC", "G1 Evacuation Pause"),
				new GcEventLog.Event(40_000, 700, "G1 Concurrent GC", "end of concurrent GC pause", "G1 Remark"));
		List<SpikeLog.Attributed> a = SpikeLog.attribute(spikes, gc);
		assertEquals(List.of(4, 3, 2, 1), a.stream().map(x -> x.spike().tick()).toList());
		assertEquals("tick work (not GC, not saving)", a.get(0).cause(), "concurrent GC is not a pause");
		assertEquals("partly GC (100 ms)", a.get(1).cause());
		assertEquals("world save", a.get(2).cause());
		assertEquals("GC pause 350 ms", a.get(3).cause());
	}
}
