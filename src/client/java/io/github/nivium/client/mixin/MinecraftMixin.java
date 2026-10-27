package io.github.nivium.client.mixin;

import io.github.nivium.client.hud.FrameMeter;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Frame boundary for the optional client FPS overlay. {@code Minecraft.runTick(boolean)} runs exactly once
 * per presented frame on the render thread. HEAD injection only adds a callback and coexists with other
 * mods' injections into the same method (Sodium 0.8.14 also targets it). When the overlay is off the hook
 * reads one boolean.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void nivium$onFrameStart(boolean advanceGameTime, CallbackInfo ci) {
		FrameMeter.onFrameStart();
	}
}
