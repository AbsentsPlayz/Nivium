package io.github.nivium.client.opt;

import io.github.nivium.Nivium;
import io.github.nivium.client.NiviumClientConfig;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Which engine optimizations are active. Decided once at startup (changes need a game restart):
 * client config, then {@code -Dnivium.opt.<name>=false} overrides (used for A/B benchmarks), then compatibility.
 * Constants, so disabled code paths are removed by the JIT.
 */
public final class NiviumOptimizations {
	/** Cache each entity type's renderer instead of a typeSwitch + map lookup 3x per entity per frame. */
	public static final boolean RENDERER_CACHE;
	/** Skip entities completely hidden behind opaque blocks. Off when the EntityCulling mod does the same job. */
	public static final boolean ENTITY_CULLING;

	static {
		NiviumClientConfig c = NiviumClientConfig.get();
		RENDERER_CACHE = c.entityRendererCache && Boolean.parseBoolean(System.getProperty("nivium.opt.rendererCache", "true"));
		boolean entityCullingMod = FabricLoader.getInstance().isModLoaded("entityculling");
		ENTITY_CULLING = c.entityOcclusionCulling && !entityCullingMod
				&& Boolean.parseBoolean(System.getProperty("nivium.opt.entityCulling", "true"));
		Nivium.LOGGER.info("Optimizations: entity renderer cache {}, entity occlusion culling {}{}",
				RENDERER_CACHE ? "on" : "off", ENTITY_CULLING ? "on" : "off",
				entityCullingMod ? " (EntityCulling mod installed, it handles this)" : "");
	}

	private NiviumOptimizations() {
	}
}
