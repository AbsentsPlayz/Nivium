package io.github.vortexoptimizer.bench;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/** Immutable outcome of a {@link BenchmarkSession}, plus JSON/CSV serialization. */
public record BenchmarkResult(
		String label,
		long startEpochMillis,
		int warmupFrames,
		double wallSeconds,
		FrameStats stats,
		boolean bufferOverflowed,
		double processCpuUtilization,
		long renderThreadAllocatedBytes,
		Map<String, long[]> gcDelta,
		List<GcEventLog.Placed> gcEvents,
		long heapStartBytes,
		long heapEndBytes,
		long heapPeakSampledBytes,
		long heapMaxBytes,
		Map<String, Object> environment,
		long[] frameNanos,
		long[] frameAllocatedBytes
) {
	public static final int SCHEMA_VERSION = 1;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

	public JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);
		root.addProperty("label", this.label);
		root.addProperty("startUtc", Instant.ofEpochMilli(this.startEpochMillis).toString());
		root.addProperty("warmupFramesDiscarded", this.warmupFrames);
		root.addProperty("wallSeconds", this.wallSeconds);
		root.addProperty("bufferOverflowed", this.bufferOverflowed);

		FrameStats s = this.stats;
		JsonObject frames = new JsonObject();
		frames.addProperty("count", s.frames());
		frames.addProperty("averageFps", s.averageFps());
		frames.addProperty("low1PercentileFps", s.low1PercentileFps());
		frames.addProperty("low1AverageFps", s.low1AverageFps());
		frames.addProperty("low01AverageFps", s.low01AverageFps());
		frames.addProperty("lowsReliable", s.reliableLows());
		frames.addProperty("meanMs", s.meanFrameMs());
		frames.addProperty("stdDevMs", s.stdDevFrameMs());
		frames.addProperty("p50Ms", s.p50FrameMs());
		frames.addProperty("p95Ms", s.p95FrameMs());
		frames.addProperty("p99Ms", s.p99FrameMs());
		frames.addProperty("p999Ms", s.p999FrameMs());
		frames.addProperty("maxMs", s.maxFrameMs());
		frames.addProperty("spikeFrames", s.spikeFrames());
		frames.addProperty("spikeThresholdMs", s.spikeThresholdMs());
		root.add("frames", frames);

		JsonObject cpu = new JsonObject();
		cpu.addProperty("processUtilization", this.processCpuUtilization);
		cpu.addProperty("note", "Process CPU time / (wall time * logical cores); -1 if unsupported.");
		root.add("cpu", cpu);

		JsonObject mem = new JsonObject();
		mem.addProperty("renderThreadAllocatedBytes", this.renderThreadAllocatedBytes);
		mem.addProperty("renderThreadAllocatedBytesPerFrame",
				this.renderThreadAllocatedBytes >= 0 && s.frames() > 0 ? (double) this.renderThreadAllocatedBytes / s.frames() : -1);
		mem.addProperty("renderThreadAllocatedMiBPerSecond",
				this.renderThreadAllocatedBytes >= 0 && this.wallSeconds > 0 ? this.renderThreadAllocatedBytes / 1048576.0 / this.wallSeconds : -1);
		mem.addProperty("heapStartBytes", this.heapStartBytes);
		mem.addProperty("heapEndBytes", this.heapEndBytes);
		mem.addProperty("heapPeakSampledBytes", this.heapPeakSampledBytes);
		mem.addProperty("heapMaxBytes", this.heapMaxBytes);
		root.add("memory", mem);

		JsonArray gc = new JsonArray();
		for (Map.Entry<String, long[]> e : this.gcDelta.entrySet()) {
			JsonObject c = new JsonObject();
			c.addProperty("collector", e.getKey());
			c.addProperty("collections", e.getValue()[0]);
			c.addProperty("timeMs", e.getValue()[1]);
			gc.add(c);
		}
		root.add("gc", gc);

		JsonArray events = new JsonArray();
		for (GcEventLog.Placed p : this.gcEvents) {
			JsonObject ev = new JsonObject();
			ev.addProperty("offsetSeconds", p.offsetSeconds());
			ev.addProperty("durationMs", p.event().durationMs());
			ev.addProperty("collector", p.event().collector());
			ev.addProperty("action", p.event().action());
			ev.addProperty("cause", p.event().cause());
			ev.addProperty("frameIndex", p.frameIndex());
			ev.addProperty("frameMs", p.frameMs());
			events.add(ev);
		}
		root.add("gcEvents", events);
		frames.addProperty("spikeFramesWithGcEvent", this.spikeFramesWithGcEvent());

		root.add("environment", GSON.toJsonTree(this.environment));

		JsonObject gpu = new JsonObject();
		gpu.addProperty("utilization", "not measured");
		gpu.addProperty("note", "GPU utilization is not exposed through OpenGL core; record it externally (e.g. Task Manager / PresentMon) if needed.");
		root.add("gpu", gpu);
		return root;
	}

	/**
	 * Writes {@code <stamp>-<label>.json} (summary) and {@code <stamp>-<label>.csv} (per-frame data)
	 * into {@code dir}.
	 *
	 * @return the JSON file path
	 */
	public Path write(Path dir) throws IOException {
		Files.createDirectories(dir);
		String base = FILE_STAMP.format(Instant.ofEpochMilli(this.startEpochMillis)) + "-" + sanitize(this.label);
		Path json = dir.resolve(base + ".json");
		Path csv = dir.resolve(base + ".csv");
		try (Writer w = Files.newBufferedWriter(json, StandardCharsets.UTF_8)) {
			GSON.toJson(this.toJson(), w);
		}
		try (Writer w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			w.write("frame,frame_ms,render_thread_alloc_bytes\n");
			for (int i = 0; i < this.frameNanos.length; i++) {
				w.write(Integer.toString(i));
				w.write(',');
				w.write(Double.toString(this.frameNanos[i] / 1e6));
				w.write(',');
				w.write(Long.toString(this.frameAllocatedBytes[i]));
				w.write('\n');
			}
		}
		return json;
	}

	/**
	 * Spike frames during which at least one GC event started. A spike without one was not caused by
	 * a collector pause; one with an event may or may not have been (compare durations).
	 */
	public int spikeFramesWithGcEvent() {
		double thresholdNanos = this.stats.spikeThresholdMs() * 1e6;
		BitSet counted = new BitSet();
		for (GcEventLog.Placed p : this.gcEvents) {
			if (this.frameNanos[p.frameIndex()] > thresholdNanos) {
				counted.set(p.frameIndex());
			}
		}
		return counted.cardinality();
	}

	static String sanitize(String label) {
		String cleaned = label.replaceAll("[^A-Za-z0-9._-]", "_");
		return cleaned.isEmpty() ? "run" : cleaned.length() > 48 ? cleaned.substring(0, 48) : cleaned;
	}
}
