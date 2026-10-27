package io.github.nivium.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.nivium.optimize.AdaptiveController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server config, {@code config/nivium.json}. All values apply live through {@code /nivium reload};
 * nothing in this file needs a server restart.
 */
public final class NiviumConfig {
	public static final int SCHEMA_VERSION = 1;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public int schemaVersion = SCHEMA_VERSION;
	public Monitoring monitoring = new Monitoring();
	public Adaptive adaptiveSimulationDistance = Adaptive.simulationDefaults();
	public Adaptive adaptiveViewDistance = Adaptive.viewDefaults();

	public static final class Monitoring {
		/** Ticks longer than this are recorded as spikes (50 ms is the 20 TPS budget). */
		public double spikeThresholdMs = 100;
		/** Log at most one spike summary per this many seconds; 0 disables spike logging. */
		public int spikeSummaryIntervalSeconds = 60;
		/** Log a one-line status every N minutes; 0 (default) disables it. */
		public int statusLogIntervalMinutes = 0;
	}

	public static final class Adaptive {
		/** Gson uses this constructor for partial objects: unknown means off, the conservative choice. */
		public boolean enabled = false;
		/** Singleplayer/LAN worlds take this setting from the host's client options, so off by default there. */
		public boolean applyOnIntegratedServer = false;
		public int minDistance = 6;
		public double overloadMspt = 45;
		public double recoverMspt = 30;
		public double overloadSeconds = 30;
		public double recoverSeconds = 120;
		public double cooldownSeconds = 60;

		static Adaptive simulationDefaults() {
			Adaptive a = new Adaptive();
			a.enabled = true;
			a.minDistance = 6;
			return a;
		}

		static Adaptive viewDefaults() {
			Adaptive a = new Adaptive();
			a.enabled = false;
			a.minDistance = 8;
			return a;
		}

		public AdaptiveController.Settings toSettings() {
			return new AdaptiveController.Settings(this.minDistance, this.overloadMspt, this.recoverMspt,
					this.overloadSeconds, this.recoverSeconds, this.cooldownSeconds);
		}
	}

	/**
	 * @param config   the config to use
	 * @param status   what happened to the file: created, loaded, upgraded, unreadable
	 * @param warnings human-readable problems that were corrected in memory (the file is not rewritten for these)
	 */
	public record LoadResult(NiviumConfig config, String status, List<String> warnings) {
	}

	public static LoadResult load(Path file) {
		List<String> warnings = new ArrayList<>();
		if (!Files.exists(file)) {
			NiviumConfig defaults = new NiviumConfig();
			try {
				defaults.save(file);
			} catch (IOException e) {
				warnings.add("could not write default config: " + e.getMessage());
			}
			return new LoadResult(defaults, "created", warnings);
		}

		JsonObject fileTree;
		NiviumConfig config;
		try {
			fileTree = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			config = GSON.fromJson(fileTree, NiviumConfig.class);
			if (config == null) {
				throw new IllegalStateException("empty config");
			}
		} catch (IOException | RuntimeException e) {
			// Never overwrite a file the admin is probably in the middle of editing.
			warnings.add("config is unreadable (" + e.getMessage() + "); using defaults and leaving the file untouched");
			return new LoadResult(new NiviumConfig(), "unreadable", warnings);
		}

		config.fillMissingSections();
		config.validate(warnings);

		JsonObject complete = GSON.toJsonTree(config).getAsJsonObject();
		if (!hasAllKeys(complete, fileTree)) {
			try {
				config.save(file); // save() backs up the current file first
				return new LoadResult(config, "upgraded", warnings);
			} catch (IOException e) {
				warnings.add("could not add new settings to the config file: " + e.getMessage());
			}
		}
		return new LoadResult(config, "loaded", warnings);
	}

	/** Writes the config atomically, after copying any existing file to {@code <name>.bak}. */
	public void save(Path file) throws IOException {
		Files.createDirectories(file.toAbsolutePath().getParent());
		if (Files.exists(file)) {
			Files.copy(file, backupPath(file), StandardCopyOption.REPLACE_EXISTING);
		}
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	public static Path backupPath(Path file) {
		return file.resolveSibling(file.getFileName() + ".bak");
	}

	private void fillMissingSections() {
		if (this.monitoring == null) {
			this.monitoring = new Monitoring();
		}
		if (this.adaptiveSimulationDistance == null) {
			this.adaptiveSimulationDistance = Adaptive.simulationDefaults();
		}
		if (this.adaptiveViewDistance == null) {
			this.adaptiveViewDistance = Adaptive.viewDefaults();
		}
	}

	private void validate(List<String> warnings) {
		Monitoring m = this.monitoring;
		if (m.spikeThresholdMs < 50) {
			warnings.add("monitoring.spikeThresholdMs " + m.spikeThresholdMs + " is below one tick budget; using 50");
			m.spikeThresholdMs = 50;
		}
		if (m.spikeSummaryIntervalSeconds < 0) {
			m.spikeSummaryIntervalSeconds = 0;
		}
		if (m.statusLogIntervalMinutes < 0) {
			m.statusLogIntervalMinutes = 0;
		}
		validate("adaptiveSimulationDistance", this.adaptiveSimulationDistance, 5, warnings);
		validate("adaptiveViewDistance", this.adaptiveViewDistance, 2, warnings);
	}

	private static void validate(String name, Adaptive a, int vanillaMin, List<String> warnings) {
		if (a.minDistance < vanillaMin || a.minDistance > 32) {
			warnings.add(name + ".minDistance " + a.minDistance + " is outside " + vanillaMin + "-32; clamped");
			a.minDistance = Math.max(vanillaMin, Math.min(32, a.minDistance));
		}
		if (a.recoverMspt >= a.overloadMspt) {
			warnings.add(name + ": recoverMspt must be below overloadMspt; using 30 / 45");
			a.recoverMspt = 30;
			a.overloadMspt = 45;
		}
		if (a.overloadSeconds < 10) {
			warnings.add(name + ".overloadSeconds below 10 would react to short spikes; using 10");
			a.overloadSeconds = 10;
		}
		if (a.recoverSeconds < 30) {
			a.recoverSeconds = 30;
		}
		if (a.cooldownSeconds < 30) {
			a.cooldownSeconds = 30;
		}
	}

	/** True if every key of {@code expected} (recursively for objects) is present in {@code actual}. */
	static boolean hasAllKeys(JsonObject expected, JsonObject actual) {
		for (Map.Entry<String, JsonElement> e : expected.entrySet()) {
			JsonElement other = actual.get(e.getKey());
			if (other == null) {
				return false;
			}
			if (e.getValue().isJsonObject() && (!other.isJsonObject() || !hasAllKeys(e.getValue().getAsJsonObject(), other.getAsJsonObject()))) {
				return false;
			}
		}
		return true;
	}
}
