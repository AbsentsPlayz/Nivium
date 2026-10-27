package io.github.nivium.client.mixin;

import io.github.nivium.client.opt.RendererCacheHolder;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Adds the per-type renderer cache slot. ~150 entity types exist, so this costs a few kilobytes in total. */
@Mixin(EntityType.class)
abstract class EntityTypeMixin implements RendererCacheHolder {
	@Unique
	private @Nullable EntityRenderer<?, ?> nivium$renderer;
	@Unique
	private int nivium$rendererGeneration;

	@Override
	public @Nullable EntityRenderer<?, ?> nivium$cachedRenderer(int generation) {
		return generation == this.nivium$rendererGeneration ? this.nivium$renderer : null;
	}

	@Override
	public void nivium$cacheRenderer(EntityRenderer<?, ?> renderer, int generation) {
		this.nivium$renderer = renderer;
		this.nivium$rendererGeneration = generation;
	}
}
