package io.github.nivium.client.mixin;

import io.github.nivium.client.cull.CullData;
import io.github.nivium.client.cull.CullHolder;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Adds the occlusion-culling cache slot, allocated the first time the entity is checked (render thread). */
@Mixin(Entity.class)
abstract class EntityMixin implements CullHolder {
	@Unique
	private CullData nivium$cull;

	@Override
	public CullData nivium$cullData() {
		CullData d = this.nivium$cull;
		if (d == null) {
			d = new CullData();
			this.nivium$cull = d;
		}
		return d;
	}
}
