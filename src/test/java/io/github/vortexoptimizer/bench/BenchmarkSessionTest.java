package io.github.vortexoptimizer.bench;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkSessionTest {
	private static final long MS = 1_000_000L;

	@Test
	void warmupFramesAreDiscarded() {
		BenchmarkSession session = new BenchmarkSession("t", 0, 3, 64);
		long t = 0;
		session.onFrameBoundary(t);           // baseline
		for (int i = 0; i < 3; i++) {         // 3 warm-up frames, slow
			t += 100 * MS;
			assertTrue(session.isWarmingUp() || i == 2);
			session.onFrameBoundary(t);
		}
		assertFalse(session.isWarmingUp());
		for (int i = 0; i < 5; i++) {
			t += 10 * MS;
			session.onFrameBoundary(t);
		}
		session.finish(t);
		BenchmarkResult r = session.result(Map.of());
		assertEquals(5, r.stats().frames());
		assertEquals(10.0, r.stats().maxFrameMs(), 1e-9, "warm-up frames must not leak into the stats");
		assertEquals(0.05, r.wallSeconds(), 1e-9);
	}

	@Test
	void durationAutoStops() {
		BenchmarkSession session = new BenchmarkSession("t", 100 * MS, 0, 64);
		long t = 0;
		int frames = 0;
		while (session.onFrameBoundary(t)) {
			t += 10 * MS;
			frames++;
			assertTrue(frames < 100, "session never stopped");
		}
		assertTrue(session.isFinished());
		assertEquals(10, session.result(Map.of()).stats().frames());
	}

	@Test
	void overflowStopsAndIsFlagged() {
		BenchmarkSession session = new BenchmarkSession("t", 0, 0, 4);
		long t = 0;
		boolean running = true;
		for (int i = 0; i < 10 && running; i++) {
			running = session.onFrameBoundary(t);
			t += MS;
		}
		assertFalse(running);
		BenchmarkResult r = session.result(Map.of());
		assertTrue(r.bufferOverflowed());
		assertEquals(4, r.stats().frames());
	}

	@Test
	void finishDuringWarmupGivesEmptyResult() {
		BenchmarkSession session = new BenchmarkSession("t", 0, 100, 16);
		session.onFrameBoundary(0);
		session.onFrameBoundary(MS);
		session.finish(2 * MS);
		assertEquals(0, session.result(Map.of()).stats().frames());
	}

	@Test
	void writesJsonAndCsv(@TempDir Path dir) throws Exception {
		BenchmarkSession session = new BenchmarkSession("my run/1", 0, 0, 16);
		long t = 0;
		for (int i = 0; i < 4; i++) {
			session.onFrameBoundary(t);
			t += 16 * MS;
		}
		session.finish(t);
		Path json = session.result(Map.of("renderDistance", 8)).write(dir);

		assertTrue(json.getFileName().toString().endsWith("-my_run_1.json"));
		JsonObject root = com.google.gson.JsonParser.parseString(Files.readString(json)).getAsJsonObject();
		assertEquals(BenchmarkResult.SCHEMA_VERSION, root.get("schemaVersion").getAsInt());
		assertEquals(3, root.getAsJsonObject("frames").get("count").getAsInt());
		assertEquals(8, root.getAsJsonObject("environment").get("renderDistance").getAsInt());

		Path csv = dir.resolve(json.getFileName().toString().replace(".json", ".csv"));
		List<String> lines = Files.readAllLines(csv);
		assertEquals(4, lines.size(), "header + 3 frames");
		assertTrue(lines.get(1).startsWith("0,16.0,"));
	}

	@Test
	void sanitizeLabels() {
		assertEquals("run", BenchmarkResult.sanitize(""));
		assertEquals("a_b", BenchmarkResult.sanitize("a b"));
		assertEquals(48, BenchmarkResult.sanitize("x".repeat(100)).length());
	}
}
