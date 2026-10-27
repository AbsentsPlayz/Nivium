package io.github.nivium.optimize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveControllerTest {
	// min 6, overload >= 45 ms for 30 s, recover <= 30 ms for 120 s, 60 s cooldown
	private static final AdaptiveController.Settings S = new AdaptiveController.Settings(6, 45, 30, 30, 120, 60);

	private static AdaptiveController started(int configured) {
		AdaptiveController c = new AdaptiveController("simulation distance", S);
		assertNull(c.evaluate(0, 10, configured));
		return c;
	}

	/** Feeds one evaluation per second from {@code from} to {@code to} (inclusive) and returns the first decision. */
	private static AdaptiveController.Decision run(AdaptiveController c, int from, int to, double mspt) {
		for (int t = from; t <= to; t++) {
			AdaptiveController.Decision d = c.evaluate(t, mspt, c.current());
			if (d != null) {
				return d;
			}
		}
		return null;
	}

	@Test
	void shortOverloadDoesNothing() {
		AdaptiveController c = started(10);
		assertNull(run(c, 1, 29, 80), "29 s of overload is below the 30 s requirement");
		assertNull(c.evaluate(30, 20, 10), "load dropped: timer resets");
		assertNull(run(c, 31, 60, 80));
		assertEquals(10, c.current());
	}

	@Test
	void sustainedOverloadStepsDownOneAtATimeWithCooldown() {
		AdaptiveController c = started(10);
		AdaptiveController.Decision d = run(c, 1, 100, 60);
		assertNotNull(d);
		assertEquals(10, d.from());
		assertEquals(9, d.to());
		assertTrue(c.isReduced());
		// Next step needs a fresh 30 s window AND the 60 s cooldown: earliest at t = 31 + 60.
		int stepAt = -1;
		for (int t = 32; t <= 200; t++) {
			if (c.evaluate(t, 60, c.current()) != null) {
				stepAt = t;
				break;
			}
		}
		assertEquals(91, stepAt);
		assertEquals(8, c.current());
	}

	@Test
	void neverGoesBelowFloorOrAboveBase() {
		AdaptiveController c = started(8);
		run(c, 1, 2000, 100);
		for (int t = 2001; t < 5000; t++) {
			c.evaluate(t, 100, c.current());
		}
		assertEquals(6, c.current(), "floor is minDistance");
		for (int t = 5000; t < 20000; t++) {
			c.evaluate(t, 5, c.current());
		}
		assertEquals(8, c.current(), "recovers to the configured value, never above");
		assertFalse(c.isReduced());
	}

	@Test
	void floorIsBaseWhenAdminConfiguredBelowMinimum() {
		AdaptiveController c = started(4);
		assertEquals(4, c.floor());
		assertNull(run(c, 1, 500, 100));
	}

	@Test
	void hysteresisBandResetsTimers() {
		AdaptiveController c = started(10);
		run(c, 1, 31, 60); // stepped to 9 at t = 31
		assertEquals(9, c.current());
		// 35 ms is between recover (30) and overload (45): nothing should ever happen.
		assertNull(run(c, 32, 2000, 35));
		assertEquals(9, c.current());
	}

	@Test
	void recoveryNeedsTheFullWindow() {
		AdaptiveController c = started(10);
		run(c, 1, 31, 60);
		assertNull(run(c, 100, 218, 10), "119 s of recovery is not enough");
		AdaptiveController.Decision d = c.evaluate(220, 10, c.current());
		assertNotNull(d);
		assertEquals(10, d.to());
	}

	@Test
	void externalChangeBecomesTheNewBase() {
		AdaptiveController c = started(10);
		run(c, 1, 31, 60);
		assertEquals(9, c.current());
		assertNull(c.evaluate(40, 60, 12), "someone else set 12");
		assertEquals(12, c.base());
		assertEquals(12, c.current());
		assertFalse(c.isReduced());
	}

	@Test
	void restorePutsTheConfiguredValueBack() {
		AdaptiveController c = started(10);
		assertNull(c.restore());
		run(c, 1, 31, 60);
		AdaptiveController.Decision d = c.restore();
		assertNotNull(d);
		assertEquals(9, d.from());
		assertEquals(10, d.to());
		assertFalse(c.isReduced());
	}
}
