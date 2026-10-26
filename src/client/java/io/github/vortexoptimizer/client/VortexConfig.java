package io.github.vortexoptimizer.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.vortexoptimizer.VortexOptimizer;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Persistent client settings, stored in {@code config/vortexoptimizer.json}. */
public final class VortexConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("vortexoptimizer.json");
	private static VortexConfig instance;

	/** Small FPS / frame-time readout in the top-left corner. */
	public boolean fpsOverlay = false;

	/** The player's own video settings, saved when the low-end preset was applied; null when not applied. */
	public @Nullable Map<String, String> savedSettings = null;

	public static VortexConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static VortexConfig load() {
		if (Files.exists(FILE)) {
			try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
				VortexConfig c = GSON.fromJson(r, VortexConfig.class);
				if (c != null) {
					return c;
				}
			} catch (Exception e) {
				VortexOptimizer.LOGGER.warn("Could not read {}, using defaults", FILE, e);
			}
		}
		return new VortexConfig();
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(this, w);
			}
		} catch (Exception e) {
			VortexOptimizer.LOGGER.error("Could not save {}", FILE, e);
		}
	}
}
