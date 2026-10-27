package io.github.nivium.client.hud;

import io.github.nivium.client.lag.LagMonitor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

/** HUD element: the optional overlay. Line 1 is this PC (FPS), line 2 the server (TPS, ping). */
public final class NiviumHud {
	private static final int BACKGROUND = 0x90000000;
	private static final int GREEN = 0xFF55FF55;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int RED = 0xFFFF5555;
	private static final int GRAY = 0xFFAAAAAA;

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
		String pc = String.format(Locale.ROOT, "%.0f FPS  %.1f ms  worst %.0f ms",
				FrameMeter.fps(), FrameMeter.avgMs(), FrameMeter.worstMs());
		int pcColor = FrameMeter.fps() >= 60 ? GREEN : FrameMeter.fps() >= 30 ? YELLOW : RED;
		g.fill(2, 2, 6 + font.width(pc), 14, BACKGROUND);
		g.drawString(font, pc, 4, 4, pcColor, false);

		if (mc.level == null) {
			return;
		}
		double tps = LagMonitor.serverTps();
		double target = mc.level.tickRateManager().tickrate();
		int ping = LagMonitor.pingMs(mc);
		String server = (mc.hasSingleplayerServer() ? "World " : "Server ")
				+ (Double.isNaN(tps) ? "TPS ..." : String.format(Locale.ROOT, "%.1f TPS", Math.min(tps, target)))
				+ (!mc.hasSingleplayerServer() && ping >= 0 ? "  " + ping + " ms ping" : "");
		int color = Double.isNaN(tps) ? GRAY : tps >= target * 0.9 ? GREEN : tps >= target * 0.6 ? YELLOW : RED;
		if (ping > 150) {
			color = YELLOW;
		}
		g.fill(2, 15, 6 + font.width(server), 27, BACKGROUND);
		g.drawString(font, server, 4, 17, color, false);
	}
}
