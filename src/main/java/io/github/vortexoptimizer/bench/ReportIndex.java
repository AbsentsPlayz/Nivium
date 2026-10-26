package io.github.vortexoptimizer.bench;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Reads the headline numbers of saved benchmark reports, newest first. */
public final class ReportIndex {
	/**
	 * @param spikesWithGc -1 for reports written before GC attribution existed
	 * @param renderDistance -1 if absent
	 */
	public record Summary(String fileName, String label, String startUtc, double averageFps, double low1AverageFps,
						  boolean lowsReliable, int spikes, int spikesWithGc, int renderDistance) {
	}

	private ReportIndex() {
	}

	public static List<Summary> latest(Path dir, int limit) {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		List<Path> files;
		try (Stream<Path> s = Files.list(dir)) {
			// File names start with a UTC timestamp, so name order is time order.
			files = s.filter(p -> p.getFileName().toString().endsWith(".json"))
					.sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
					.limit(limit)
					.toList();
		} catch (IOException e) {
			return List.of();
		}
		List<Summary> out = new ArrayList<>();
		for (Path file : files) {
			Summary summary = read(file);
			if (summary != null) {
				out.add(summary);
			}
		}
		return out;
	}

	static Summary read(Path file) {
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject frames = root.getAsJsonObject("frames");
			JsonObject settings = root.has("environment") && root.getAsJsonObject("environment").has("settings")
					? root.getAsJsonObject("environment").getAsJsonObject("settings") : new JsonObject();
			return new Summary(
					file.getFileName().toString(),
					str(root.get("label")),
					str(root.get("startUtc")),
					frames.get("averageFps").getAsDouble(),
					frames.get("low1AverageFps").getAsDouble(),
					frames.get("lowsReliable").getAsBoolean(),
					frames.get("spikeFrames").getAsInt(),
					frames.has("spikeFramesWithGcEvent") ? frames.get("spikeFramesWithGcEvent").getAsInt() : -1,
					settings.has("renderDistance") ? settings.get("renderDistance").getAsInt() : -1);
		} catch (IOException | RuntimeException e) {
			return null; // unreadable or foreign file: skip it rather than break the screen
		}
	}

	private static String str(JsonElement e) {
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}
}
