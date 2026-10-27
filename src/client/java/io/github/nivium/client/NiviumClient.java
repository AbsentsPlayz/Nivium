package io.github.nivium.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nivium.Nivium;
import io.github.nivium.client.gui.NiviumScreen;
import io.github.nivium.client.hud.FrameMeter;
import io.github.nivium.client.hud.NiviumHud;
import io.github.nivium.client.lag.LagMonitor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Nivium for players: the Nivium screen (K) with a lag check, freeze detection and recommendations, an FPS
 * overlay and a reversible FPS preset. Client-only; works on any server without the server having Nivium.
 */
public final class NiviumClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Nivium.MOD_ID, "main"));
	private static KeyMapping openScreenKey;

	@Override
	public void onInitializeClient() {
		openScreenKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.nivium.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openScreenKey.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new NiviumScreen(null));
				}
			}
		});

		LagMonitor.GC.start();
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(LagMonitor::reset));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(LagMonitor::reset));

		FrameMeter.enabled = NiviumClientConfig.get().fpsOverlay;
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Nivium.MOD_ID, "hud"), NiviumHud::render);
	}
}
