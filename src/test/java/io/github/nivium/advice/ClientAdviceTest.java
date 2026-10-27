package io.github.nivium.advice;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientAdviceTest {
	private static final long GB = 1024L * 1024 * 1024;

	private static List<String> advice(long heap, long total, long free, int rd, double fps, int stutters, int gc,
									   boolean sodium, boolean preset) {
		return ClientAdvice.compute(new ClientAdvice.Inputs(heap, total, free, rd, fps, stutters, gc, sodium, preset));
	}

	@Test
	void healthySetupHasNoAdvice() {
		assertEquals(List.of(), advice(4 * GB, 16 * GB, 8 * GB, 10, 120, 0, 0, true, false));
	}

	@Test
	void theMeasuredLowRamLaptop() {
		// The dev machine: ~2 GB heap, 7.7 GB RAM with 0.3-0.5 GB free, freezes from GC.
		List<String> a = advice(2 * GB, (long) (7.7 * GB), GB / 3, 16, 44, 5, 2, true, false);
		assertTrue(a.stream().anyMatch(s -> s.startsWith("Only 0.3 of 7.7 GB RAM is free")));
		assertTrue(a.stream().anyMatch(s -> s.contains("memory cleanup (GC)")));
		assertTrue(a.stream().anyMatch(s -> s.startsWith("Render distance 16")));
		assertTrue(a.stream().anyMatch(s -> s.contains("Client FPS preset")));
		assertFalse(a.stream().anyMatch(s -> s.contains("Sodium")));
	}

	@Test
	void tooMuchAndTooLittleHeap() {
		assertTrue(advice(6 * GB, 8 * GB, 4 * GB, 8, 100, 0, 0, true, false).get(0).startsWith("Minecraft may use 6.0 of your 8.0 GB"));
		assertTrue(advice(GB, 16 * GB, 8 * GB, 8, 100, 0, 0, true, false).get(0).startsWith("Minecraft only has 1.0 GB"));
	}

	@Test
	void sodiumAndPresetSuggestions() {
		List<String> a = advice(4 * GB, 16 * GB, 8 * GB, 8, 40, 0, 0, false, false);
		assertTrue(a.get(0).startsWith("Install Sodium"));
		assertTrue(a.stream().anyMatch(s -> s.contains("Client FPS preset")));
		assertFalse(advice(4 * GB, 16 * GB, 8 * GB, 8, 40, 0, 0, true, true).stream().anyMatch(s -> s.contains("preset")),
				"no preset advice once it is applied");
	}

	@Test
	void unknownMemoryIsSkipped() {
		assertEquals(List.of(), advice(4 * GB, -1, -1, 8, 100, 0, 0, true, false));
	}
}
