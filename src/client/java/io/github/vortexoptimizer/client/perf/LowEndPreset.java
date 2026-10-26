package io.github.vortexoptimizer.client.perf;

import io.github.vortexoptimizer.VortexOptimizer;
import io.github.vortexoptimizer.client.VortexConfig;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A reversible set of vanilla video settings that reduce real work on weak CPUs/iGPUs. Each entry
 * trades a visible feature for less work; nothing here hides work from measurements. The player's
 * previous values are saved to the config first, so {@link #restore} works even after a restart.
 *
 * <p>Deliberately not touched: mipmap levels (changing them reloads all resources), VSync, the FPS
 * limit and window settings (those change what a benchmark measures, not how much work is done).
 */
public final class LowEndPreset {
	private record Entry<T>(String key, String description, Function<Options, OptionInstance<T>> option,
							Function<T, T> lowEnd, Function<String, T> parse) {
		String current(Options o) {
			T v = this.option.apply(o).get();
			return v instanceof Enum<?> e ? e.name() : String.valueOf(v);
		}

		void applyLowEnd(Options o) {
			OptionInstance<T> opt = this.option.apply(o);
			opt.set(this.lowEnd.apply(opt.get()));
		}

		void restore(Options o, String saved) {
			this.option.apply(o).set(this.parse.apply(saved));
		}
	}

	private static final List<Entry<?>> ENTRIES = List.of(
			new Entry<>("renderDistance", "Render distance at most 8 chunks (less terrain to mesh and draw)",
					Options::renderDistance, v -> Math.min(v, 8), Integer::valueOf),
			new Entry<>("simulationDistance", "Simulation distance at most 6 (lighter integrated server in singleplayer)",
					Options::simulationDistance, v -> Math.min(v, 6), Integer::valueOf),
			new Entry<>("cloudStatus", "Clouds off", Options::cloudStatus, v -> CloudStatus.OFF, CloudStatus::valueOf),
			new Entry<>("particles", "Particles decreased", Options::particles,
					v -> v == ParticleStatus.MINIMAL ? v : ParticleStatus.DECREASED, ParticleStatus::valueOf),
			new Entry<>("entityShadows", "Entity shadows off", Options::entityShadows, v -> false, Boolean::valueOf),
			new Entry<>("biomeBlendRadius", "Biome blend off (faster chunk rebuilds)", Options::biomeBlendRadius,
					v -> 0, Integer::valueOf),
			new Entry<>("entityDistanceScaling", "Entity distance 75 % at most", Options::entityDistanceScaling,
					v -> Math.min(v, 0.75), Double::valueOf)
	);

	private LowEndPreset() {
	}

	public static List<String> descriptions() {
		return ENTRIES.stream().map(Entry::description).toList();
	}

	public static boolean isApplied() {
		return VortexConfig.get().savedSettings != null;
	}

	public static void apply(Minecraft mc) {
		VortexConfig config = VortexConfig.get();
		Options o = mc.options;
		if (config.savedSettings == null) {
			Map<String, String> saved = new LinkedHashMap<>();
			for (Entry<?> e : ENTRIES) {
				saved.put(e.key(), e.current(o));
			}
			config.savedSettings = saved;
			config.save();
		}
		for (Entry<?> e : ENTRIES) {
			e.applyLowEnd(o);
		}
		o.save();
		VortexOptimizer.LOGGER.info("Applied low-end preset; previous settings saved: {}", config.savedSettings);
	}

	public static void restore(Minecraft mc) {
		VortexConfig config = VortexConfig.get();
		if (config.savedSettings == null) {
			return;
		}
		Options o = mc.options;
		for (Entry<?> e : ENTRIES) {
			String saved = config.savedSettings.get(e.key());
			if (saved == null) {
				continue;
			}
			try {
				e.restore(o, saved);
			} catch (RuntimeException ex) {
				VortexOptimizer.LOGGER.warn("Could not restore {}={}", e.key(), saved, ex);
			}
		}
		o.save();
		config.savedSettings = null;
		config.save();
		VortexOptimizer.LOGGER.info("Restored settings from before the low-end preset");
	}
}
