package io.github.nivium.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.nivium.client.cull.EntityOcclusionCuller;
import io.github.nivium.client.opt.NiviumOptimizations;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Entity occlusion culling: an entity vanilla (and Sodium) would render is skipped if {@link EntityOcclusionCuller}
 * finds it completely hidden behind opaque blocks.
 *
 * <p>Deliberately no {@code @Redirect} in this class: MixinExtras 0.5.0 (bundled with Fabric Loader 0.18.x) inspects
 * every {@code @Redirect} on a target class and casts its {@code at} to a single annotation. Code compiled against
 * newer Fabric Mixin stores {@code at} as an array, so any {@code @Redirect} here crashed the game on load
 * (0.3.0 bug). {@code @ModifyReturnValue} is MixinExtras' own injector and is safe on all versions.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
	@ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
	private boolean nivium$occlusionCull(boolean visible, Entity entity, Frustum frustum, double camX, double camY, double camZ) {
		return visible && (!NiviumOptimizations.ENTITY_CULLING || !EntityOcclusionCuller.isHidden(entity, camX, camY, camZ));
	}
}
