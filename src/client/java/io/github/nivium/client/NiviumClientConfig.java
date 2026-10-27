package io.github.nivium.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.nivium.Nivium;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Client-only settings, {@code config/nivium-client.json}. On first run it takes over the settings of the
 * mod's previous name ({@code vortexoptimizer.json}), including the video settings saved by the FPS preset,
 * so "Restore my settings" keeps working. The old file is left in place untouched as a backup.
 */
public final class NiviumClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("nivium-client.json");
	private static final Path LEGACY_FILE = FabricLoader.getInstance().getConfigDir().resolve("vortexoptimizer.json");
	private static NiviumClientConfig instance;

	/** Small FPS / frame-time readout in the top-left corner. */
	public boolean fpsOverlay = false;

	/** Engine optimization: skip entities hidden behind opaque blocks. Needs a restart to change. */
	public boolean entityOcclusionCulling = true;

	/** The player's own video settings, saved when the FPS preset was applied; null when not applied. */
	public @Nullable Map<String, String> savedSettings = null;

	public static NiviumClientConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static NiviumClientConfig load() {
		if (Files.exists(FILE)) {
			return read(FILE);
		}
		if (Files.exists(LEGACY_FILE)) {
			NiviumClientConfig migrated = read(LEGACY_FILE);
			migrated.save();
			Nivium.LOGGER.info("Migrated client settings from {} to {} (old file kept)", LEGACY_FILE.getFileName(), FILE.getFileName());
			return migrated;
		}
		return new NiviumClientConfig();
	}

	private static NiviumClientConfig read(Path file) {
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			NiviumClientConfig c = GSON.fromJson(r, NiviumClientConfig.class);
			if (c != null) {
				return c;
			}
		} catch (Exception e) {
			Nivium.LOGGER.warn("Could not read {}, using defaults", file, e);
		}
		return new NiviumClientConfig();
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(this, w);
			}
		} catch (Exception e) {
			Nivium.LOGGER.error("Could not save {}", FILE, e);
		}
	}
}
