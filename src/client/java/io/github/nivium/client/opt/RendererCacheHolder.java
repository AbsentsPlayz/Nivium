package io.github.nivium.client.opt;

import net.minecraft.client.renderer.entity.EntityRenderer;
import org.jetbrains.annotations.Nullable;

/** Implemented on {@code EntityType} by mixin: the renderer for this type, tagged with the dispatcher's generation. */
public interface RendererCacheHolder {
	/** The cached renderer if it was stored under {@code generation}, otherwise null. */
	@Nullable EntityRenderer<?, ?> nivium$cachedRenderer(int generation);

	void nivium$cacheRenderer(EntityRenderer<?, ?> renderer, int generation);
}
