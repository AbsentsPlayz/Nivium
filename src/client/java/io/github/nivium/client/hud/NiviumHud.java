package io.github.nivium.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

/** HUD element: the optional client FPS readout (top-left). */
public final class NiviumHud {
	private static final int BACKGROUND = 0x90000000;
	private static final int GREEN = 0xFF55FF55;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int RED = 0xFFFF5555;

	private NiviumHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		if (!FrameMeter.enabled) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		Font font = mc.font;
		String text = String.format(Locale.ROOT, "%.0f FPS  %.1f ms  worst %.0f ms",
				FrameMeter.fps(), FrameMeter.avgMs(), FrameMeter.worstMs());
		int color = FrameMeter.fps() >= 60 ? GREEN : FrameMeter.fps() >= 30 ? YELLOW : RED;
		g.fill(2, 2, 6 + font.width(text), 14, BACKGROUND);
		g.drawString(font, text, 4, 4, color, false);
	}
}
