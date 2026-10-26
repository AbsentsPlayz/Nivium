package io.github.vortexoptimizer.client.bench;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.vortexoptimizer.bench.JvmProbe;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures the variables a benchmark must hold constant (settings, resolution) and the context
 * needed to interpret it (hardware, driver, JVM, mod set). Render thread only.
 */
public final class EnvironmentCollector {
	private EnvironmentCollector() {
	}

	public static Map<String, Object> collect(Minecraft mc) {
		Map<String, Object> env = new LinkedHashMap<>();
		env.put("settings", settings(mc));

		Map<String, Object> gpu = new LinkedHashMap<>();
		GpuDevice device = RenderSystem.tryGetDevice();
		if (device != null) {
			gpu.put("backend", device.getBackendName());
			gpu.put("vendor", device.getVendor());
			gpu.put("renderer", device.getRenderer());
			gpu.put("version", device.getVersion());
		}
		env.put("gpu", gpu);

		Map<String, Object> jvm = new LinkedHashMap<>();
		jvm.put("javaVersion", System.getProperty("java.version"));
		jvm.put("javaVendor", System.getProperty("java.vendor"));
		jvm.put("vm", System.getProperty("java.vm.name"));
		jvm.put("arguments", JvmProbe.jvmArguments());
		jvm.put("heapMaxBytes", JvmProbe.heapMaxBytes());
		env.put("jvm", jvm);

		Map<String, Object> os = new LinkedHashMap<>();
		os.put("name", System.getProperty("os.name"));
		os.put("version", System.getProperty("os.version"));
		os.put("arch", System.getProperty("os.arch"));
		os.put("logicalCores", JvmProbe.availableProcessors());
		env.put("os", os);

		// Not mc.getLaunchedVersion(): that is the launcher profile name (e.g. "Fabric"), not the game version.
		env.put("minecraftVersion", FabricLoader.getInstance().getModContainer("minecraft")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown"));
		env.put("world", mc.hasSingleplayerServer() ? "singleplayer" : mc.getCurrentServer() != null ? "multiplayer" : "none");
		env.put("mods", mods());
		return env;
	}

	static Map<String, Object> settings(Minecraft mc) {
		Options o = mc.options;
		Window w = mc.getWindow();
		Map<String, Object> s = new LinkedHashMap<>();
		s.put("windowWidth", w.getWidth());
		s.put("windowHeight", w.getHeight());
		s.put("fullscreen", w.isFullscreen());
		s.put("renderDistance", o.renderDistance().get());
		s.put("simulationDistance", o.simulationDistance().get());
		s.put("framerateLimit", o.framerateLimit().get());
		s.put("vsync", o.enableVsync().get());
		s.put("graphicsPreset", String.valueOf(o.graphicsPreset().get()));
		s.put("improvedTransparency", o.improvedTransparency().get());
		s.put("ambientOcclusion", o.ambientOcclusion().get());
		s.put("cloudStatus", String.valueOf(o.cloudStatus().get()));
		s.put("particles", String.valueOf(o.particles().get()));
		s.put("entityDistanceScaling", o.entityDistanceScaling().get());
		s.put("biomeBlendRadius", o.biomeBlendRadius().get());
		s.put("mipmapLevels", o.mipmapLevels().get());
		s.put("prioritizeChunkUpdates", String.valueOf(o.prioritizeChunkUpdates().get()));
		s.put("chunkSectionFadeInTime", o.chunkSectionFadeInTime().get());
		s.put("guiScale", o.guiScale().get());
		return s;
	}

	/** True when the controlled variables (settings + resolution) match between two captures. */
	static boolean settingsEqual(Map<String, Object> a, Map<String, Object> b) {
		Object sa = a.get("settings");
		Object sb = b.get("settings");
		return sa instanceof Map && sa.equals(sb);
	}

	private static List<String> mods() {
		return FabricLoader.getInstance().getAllMods().stream()
				.map(ModContainer::getMetadata)
				.filter(m -> !m.getId().startsWith("fabric-") || m.getId().equals("fabric-api"))
				.sorted(Comparator.comparing(m -> m.getId()))
				.map(m -> m.getId() + "@" + m.getVersion().getFriendlyString())
				.toList();
	}
}
