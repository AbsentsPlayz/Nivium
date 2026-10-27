package io.github.nivium.client.mixin;

import io.github.nivium.client.lag.LagMonitor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds server time updates (sent every 20 server ticks) to the TPS estimate. TAIL, not HEAD: the handler first
 * runs on the network thread and bails out to reschedule itself on the render thread, so only the render-thread
 * call reaches the end of the method. Reads the packet only; vanilla handling is unchanged.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
	@Inject(method = "handleSetTime", at = @At("TAIL"))
	private void nivium$onSetTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
		LagMonitor.onTimePacket(packet.gameTime());
	}
}
