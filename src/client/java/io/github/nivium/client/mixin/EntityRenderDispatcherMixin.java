package io.github.nivium.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.nivium.client.cull.EntityOcclusionCuller;
import io.github.nivium.client.opt.NiviumOptimizations;
import io.github.nivium.client.opt.RendererCacheHolder;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Both entity optimizations hook the dispatcher's per-entity hot path:
 * <ul>
 *   <li>Renderer cache: {@code shouldRender}, {@code getPackedLightCoords} and {@code extractEntity} each call
 *       {@code getRenderer(entity)} (a typeSwitch plus an ImmutableMap lookup), i.e. 3x per visible entity per frame.
 *       For non-avatar entities the answer depends only on the entity type, so it is cached on the type and
 *       invalidated by bumping a generation whenever the renderers are rebuilt (resource reload). Players and
 *       mannequins keep the vanilla path: their renderer depends on the skin model, which can change.
 *       {@code @Redirect} instead of a cancellable {@code @Inject} avoids allocating a callback object per call.</li>
 *   <li>Occlusion culling: an entity vanilla would render is skipped if {@link EntityOcclusionCuller} finds it
 *       completely hidden behind opaque blocks.</li>
 * </ul>
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
	@Unique
	private int nivium$rendererGeneration = 1;

	@Inject(method = "onResourceManagerReload", at = {@At("HEAD"), @At("TAIL")})
	private void nivium$invalidateRendererCache(ResourceManager resourceManager, CallbackInfo ci) {
		this.nivium$rendererGeneration++;
	}

	@Redirect(method = {"shouldRender", "getPackedLightCoords", "extractEntity"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;"))
	private EntityRenderer<?, ?> nivium$cachedRenderer(EntityRenderDispatcher self, Entity entity) {
		if (!NiviumOptimizations.RENDERER_CACHE || entity instanceof AbstractClientPlayer || entity instanceof ClientMannequin) {
			return self.getRenderer(entity);
		}
		RendererCacheHolder holder = (RendererCacheHolder) (Object) entity.getType(); // EntityType is final
		int generation = this.nivium$rendererGeneration;
		EntityRenderer<?, ?> renderer = holder.nivium$cachedRenderer(generation);
		if (renderer == null) {
			renderer = self.getRenderer(entity);
			if (renderer != null) {
				holder.nivium$cacheRenderer(renderer, generation);
			}
		}
		return renderer;
	}

	@ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
	private boolean nivium$occlusionCull(boolean visible, Entity entity, Frustum frustum, double camX, double camY, double camZ) {
		return visible && (!NiviumOptimizations.ENTITY_CULLING || !EntityOcclusionCuller.isHidden(entity, camX, camY, camZ));
	}
}
