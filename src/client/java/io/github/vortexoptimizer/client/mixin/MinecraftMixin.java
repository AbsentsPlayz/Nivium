package io.github.vortexoptimizer.client.mixin;

import io.github.vortexoptimizer.client.bench.BenchmarkController;
import io.github.vortexoptimizer.client.hud.FrameMeter;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Frame boundary for the benchmark recorder. {@code Minecraft.runTick(boolean)} runs exactly once
 * per presented frame on the render thread, including when the HUD is hidden or a screen is open,
 * which Fabric's HUD/world render events do not guarantee. HEAD injection only adds a callback and
 * coexists with other mods' injections into the same method.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void vortex$onFrameStart(boolean advanceGameTime, CallbackInfo ci) {
		BenchmarkController.onFrameStart();
		FrameMeter.onFrameStart();
	}
}
