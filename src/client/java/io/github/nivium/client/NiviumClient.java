package io.github.nivium.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nivium.Nivium;
import io.github.nivium.client.gui.NiviumScreen;
import io.github.nivium.client.hud.FrameMeter;
import io.github.nivium.client.hud.NiviumHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Optional client extras: the Nivium screen (K), an FPS overlay and a reversible client video preset.
 * The server features do not depend on any of this; servers work with unmodded players.
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

		FrameMeter.enabled = NiviumClientConfig.get().fpsOverlay;
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Nivium.MOD_ID, "hud"), NiviumHud::render);
	}
}
