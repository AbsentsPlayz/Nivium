package io.github.nivium.lag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LagTest {
	private static final long S = 1_000_000_000L;

	@Test
	void tpsEstimateFromTimeUpdates() {
		ServerTpsEstimator e = new ServerTpsEstimator();
		assertTrue(Double.isNaN(e.tps()));
		assertTrue(Double.isNaN(e.secondsSinceUpdate(0)));
		// A healthy server: +20 game ticks per real second.
		for (int i = 0; i <= 10; i++) {
			e.onTimeUpdate(i * S, 1000 + i * 20L);
		}
		assertEquals(20.0, e.tps(), 1e-9);
		assertEquals(0.5, e.secondsSinceUpdate(10 * S + S / 2), 1e-9);
	}

	@Test
	void laggingServerSendsUpdatesLessOften() {
		ServerTpsEstimator e = new ServerTpsEstimator();
		// 20 ticks per update, but updates arrive every 2 s -> 10 TPS.
		for (int i = 0; i <= 5; i++) {
			e.onTimeUpdate(i * 2 * S, i * 20L);
		}
		assertEquals(10.0, e.tps(), 1e-9);
	}

	@Test
	void needsAtLeastThreeSecondsOfData() {
		ServerTpsEstimator e = new ServerTpsEstimator();
		e.onTimeUpdate(0, 0);
		e.onTimeUpdate(2 * S, 40);
		assertTrue(Double.isNaN(e.tps()));
		e.onTimeUpdate(3 * S, 60);
		assertEquals(20.0, e.tps(), 1e-9);
	}

	@Test
	void usesOnlyTheLastTenSeconds() {
		ServerTpsEstimator e = new ServerTpsEstimator();
		for (int i = 0; i <= 20; i++) { // 20 s at 5 TPS ...
			e.onTimeUpdate(i * S, i * 5L);
		}
		for (int i = 21; i <= 40; i++) { // ... then 20 s at 20 TPS
			e.onTimeUpdate(i * S, 100 + (i - 20) * 20L);
		}
		assertEquals(20.0, e.tps(), 1e-9, "old slow period has left the window");
	}

	@Test
	void gameTimeGoingBackwardsResets() {
		ServerTpsEstimator e = new ServerTpsEstimator();
		for (int i = 0; i <= 5; i++) {
			e.onTimeUpdate(i * S, 50_000 + i * 20L);
		}
		e.onTimeUpdate(6 * S, 10); // joined another server
		assertTrue(Double.isNaN(e.tps()));
	}

	private static LagVerdict.Inputs inputs(double fps, int stutters, int gcStutters, double tps, double silentFor, int ping, boolean sp) {
		return new LagVerdict.Inputs(fps, stutters, gcStutters, tps, 20, silentFor, ping, sp);
	}

	@Test
	void verdictSmooth() {
		LagVerdict.Result r = LagVerdict.evaluate(inputs(90, 0, 0, 20, 0.5, 40, false));
		assertEquals(LagVerdict.Source.NONE, r.source());
		assertEquals("Everything looks smooth", r.headline());
	}

	@Test
	void verdictBlamesTheServerFirst() {
		LagVerdict.Result r = LagVerdict.evaluate(inputs(25, 0, 0, 12.5, 0.5, 40, false));
		assertEquals(LagVerdict.Source.SERVER, r.source());
		assertEquals(2, r.findings().size(), "low FPS is still listed");
		assertTrue(r.findings().get(1).startsWith("Your PC"));
	}

	@Test
	void verdictYourPcWithGcFreezes() {
		LagVerdict.Result r = LagVerdict.evaluate(inputs(80, 4, 3, 20, 0.5, 40, false));
		assertEquals(LagVerdict.Source.YOUR_PC, r.source());
		assertTrue(r.findings().get(0).contains("3 were memory cleanup"));
	}

	@Test
	void verdictConnection() {
		LagVerdict.Result r = LagVerdict.evaluate(inputs(90, 0, 0, 20, 0.5, 300, false));
		assertEquals(LagVerdict.Source.CONNECTION, r.source());
	}

	@Test
	void silentServerIsReported() {
		LagVerdict.Result r = LagVerdict.evaluate(inputs(90, 0, 0, 20, 7, 40, false));
		assertEquals(LagVerdict.Source.SERVER, r.source());
		assertTrue(r.headline().contains("not responding"));
	}

	@Test
	void singleplayerIgnoresPingAndSilence() {
		LagVerdict.Result r = LagVerdict.evaluate(inputs(90, 0, 0, 20, 30, 500, true));
		assertEquals(LagVerdict.Source.NONE, r.source());
		LagVerdict.Result slow = LagVerdict.evaluate(inputs(90, 0, 0, 10, 0.5, 0, true));
		assertTrue(slow.headline().startsWith("World (runs on your PC)"));
	}

	@Test
	void unknownTpsIsNotBlamed() {
		LagVerdict.Result r = LagVerdict.evaluate(inputs(90, 0, 0, Double.NaN, Double.NaN, -1, false));
		assertEquals(LagVerdict.Source.NONE, r.source());
	}
}
