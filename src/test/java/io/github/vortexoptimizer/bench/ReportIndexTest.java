package io.github.vortexoptimizer.bench;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportIndexTest {
	private static final long MS = 1_000_000L;

	private static void writeRun(Path dir, String label, long epochMillis) throws Exception {
		long[] frames = {10 * MS, 10 * MS, 50 * MS};
		BenchmarkResult r = new BenchmarkResult(label, epochMillis, 0, 0.07, FrameStats.compute(frames), false, -1, -1,
				Map.of(), List.of(), 0, 0, 0, 0, Map.of("settings", Map.of("renderDistance", 8)), frames, new long[3]);
		r.write(dir);
	}

	@Test
	void newestFirstWithLimit(@TempDir Path dir) throws Exception {
		writeRun(dir, "old", 1_000_000_000_000L);
		writeRun(dir, "mid", 1_000_000_100_000L);
		writeRun(dir, "new", 1_000_000_200_000L);
		List<ReportIndex.Summary> list = ReportIndex.latest(dir, 2);
		assertEquals(List.of("new", "mid"), list.stream().map(ReportIndex.Summary::label).toList());
		ReportIndex.Summary s = list.get(0);
		assertEquals(3 / 0.07, s.averageFps(), 1e-6);
		assertEquals(1, s.spikes());
		assertEquals(0, s.spikesWithGc());
		assertEquals(8, s.renderDistance());
	}

	@Test
	void skipsBrokenFilesAndMissingDirectory(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("99999999-999999-broken.json"), "{not json");
		writeRun(dir, "ok", 1_000_000_000_000L);
		assertEquals(List.of("ok"), ReportIndex.latest(dir, 10).stream().map(ReportIndex.Summary::label).toList());
		assertTrue(ReportIndex.latest(dir.resolve("missing"), 10).isEmpty());
	}

	@Test
	void readsReportsFromBeforeGcAttribution(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("20260101-000000-legacy.json");
		Files.writeString(f, """
				{"label":"legacy","startUtc":"x","frames":{"averageFps":60.0,"low1AverageFps":30.0,
				"lowsReliable":false,"spikeFrames":4}}""");
		ReportIndex.Summary s = ReportIndex.latest(dir, 1).get(0);
		assertEquals(-1, s.spikesWithGc());
		assertEquals(-1, s.renderDistance());
	}
}
