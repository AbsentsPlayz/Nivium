package io.github.vortexoptimizer.client.hud;

import io.github.vortexoptimizer.bench.BenchmarkSession;
import io.github.vortexoptimizer.client.bench.BenchmarkController;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

/** HUD element: the optional FPS readout (top-left) and a recording indicator during benchmarks (top-right). */
public final class VortexHud {
	private static final int BACKGROUND = 0x90000000;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREEN = 0xFF55FF55;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int RED = 0xFFFF5555;

	private VortexHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		Font font = mc.font;
		if (FrameMeter.enabled) {
			String text = String.format(Locale.ROOT, "%.0f FPS  %.1f ms  worst %.0f ms",
					FrameMeter.fps(), FrameMeter.avgMs(), FrameMeter.worstMs());
			int color = FrameMeter.fps() >= 60 ? GREEN : FrameMeter.fps() >= 30 ? YELLOW : RED;
			g.fill(2, 2, 6 + font.width(text), 14, BACKGROUND);
			g.drawString(font, text, 4, 4, color, false);
		}

		BenchmarkSession s = BenchmarkController.active();
		if (s != null) {
			String text = s.isWarmingUp()
					? "Vortex: warming up..."
					: BenchmarkController.durationSeconds() > 0
					? String.format(Locale.ROOT, "● REC %.0f / %.0f s", s.recordedSeconds(System.nanoTime()), BenchmarkController.durationSeconds())
					: String.format(Locale.ROOT, "● REC %.0f s", s.recordedSeconds(System.nanoTime()));
			int w = font.width(text);
			int x = g.guiWidth() - w - 4;
			g.fill(x - 2, 2, x + w + 2, 14, BACKGROUND);
			g.drawString(font, text, x, 4, s.isWarmingUp() ? YELLOW : RED, false);
		}
	}
}
