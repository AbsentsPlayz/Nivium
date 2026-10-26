package io.github.vortexoptimizer.client.gui;

import io.github.vortexoptimizer.bench.BenchmarkSession;
import io.github.vortexoptimizer.bench.ReportIndex;
import io.github.vortexoptimizer.client.VortexConfig;
import io.github.vortexoptimizer.client.bench.BenchmarkController;
import io.github.vortexoptimizer.client.hud.FrameMeter;
import io.github.vortexoptimizer.client.perf.LowEndPreset;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Main Vortex screen (default key: V): benchmark controls, recent results, overlay and FPS preset. */
public final class VortexScreen extends Screen {
	private static final int PANEL_WIDTH = 300;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int GOLD = 0xFFFFAA00;
	private static final int GREEN = 0xFF55FF55;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int RED = 0xFFFF5555;
	private static final int RECENT_ROWS = 5;

	private final @Nullable Screen parent;
	private List<ReportIndex.Summary> recent = List.of();
	private final List<Button> benchButtons = new ArrayList<>();
	private Button stopButton;
	private int left;

	public VortexScreen(@Nullable Screen parent) {
		super(Component.literal("Vortex Optimizer"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.left = (this.width - PANEL_WIDTH) / 2;
		this.recent = ReportIndex.latest(BenchmarkController.reportsDir(), RECENT_ROWS);
		int y = 34;

		int w = (PANEL_WIDTH - 6) / 4;
		this.benchButtons.clear();
		this.benchButtons.add(this.addRenderableWidget(benchButton("Quick 20s", "quick", 20, this.left, y, w,
				"Fast check. Too short for reliable 1% lows.")));
		this.benchButtons.add(this.addRenderableWidget(benchButton("Normal 60s", "normal", 60, this.left + w + 2, y, w,
				"Good for comparing settings.")));
		this.benchButtons.add(this.addRenderableWidget(benchButton("Full 120s", "full", 120, this.left + 2 * (w + 2), y, w,
				"Collects enough frames for reliable 1% lows.")));
		this.stopButton = this.addRenderableWidget(Button.builder(Component.literal("Stop"), b -> {
			BenchmarkController.stop();
			this.rebuildWidgets();
		}).bounds(this.left + 3 * (w + 2), y, w, 20).build());

		y = 34 + 24 + 12 + RECENT_ROWS * 10 + 6;
		int half = (PANEL_WIDTH - 2) / 2;
		this.addRenderableWidget(Button.builder(Component.literal("Open reports folder"), b -> {
			try {
				Files.createDirectories(BenchmarkController.reportsDir());
			} catch (Exception ignored) {
			}
			Util.getPlatform().openPath(BenchmarkController.reportsDir());
		}).bounds(this.left, y, half, 20).build());
		this.addRenderableWidget(Button.builder(overlayLabel(), b -> {
			VortexConfig c = VortexConfig.get();
			c.fpsOverlay = !c.fpsOverlay;
			FrameMeter.enabled = c.fpsOverlay;
			c.save();
			b.setMessage(overlayLabel());
		}).bounds(this.left + half + 2, y, half, 20)
				.tooltip(Tooltip.create(Component.literal("Shows FPS, average frame time and the worst frame of the last half second.")))
				.build());

		y += 24 + 12;
		boolean applied = LowEndPreset.isApplied();
		Button preset = Button.builder(Component.literal(applied ? "Restore my settings" : "Apply FPS boost preset"), b -> {
			if (LowEndPreset.isApplied()) {
				LowEndPreset.restore(this.minecraft);
			} else {
				LowEndPreset.apply(this.minecraft);
			}
			this.rebuildWidgets();
		}).bounds(this.left, y, PANEL_WIDTH, 20).build();
		preset.setTooltip(Tooltip.create(Component.literal(applied
				? "Puts back the exact settings you had before the preset."
				: "Changes (all reversible):\n- " + String.join("\n- ", LowEndPreset.descriptions()))));
		this.addRenderableWidget(preset);

		y += 28;
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.bounds(this.left + PANEL_WIDTH / 4, y, PANEL_WIDTH / 2, 20).build());

		this.updateButtonStates();
	}

	private Button benchButton(String text, String label, int seconds, int x, int y, int w, String tip) {
		return Button.builder(Component.literal(text), b -> {
			String fullLabel = label + "-rd" + this.minecraft.options.renderDistance().get();
			if (BenchmarkController.start(fullLabel, seconds, 300)) {
				// Measure gameplay, not this menu (which also pauses singleplayer).
				this.minecraft.setScreen(null);
			}
		}).bounds(x, y, w, 20).tooltip(Tooltip.create(Component.literal(
				tip + "\nStand still, don't touch the mouse. 300 warm-up frames first."))).build();
	}

	private static Component overlayLabel() {
		return Component.literal("FPS overlay: " + (VortexConfig.get().fpsOverlay ? "ON" : "OFF"));
	}

	private void updateButtonStates() {
		boolean running = BenchmarkController.isRunning();
		boolean inWorld = this.minecraft.level != null;
		for (Button b : this.benchButtons) {
			b.active = !running && inWorld;
		}
		this.stopButton.active = running;
	}

	@Override
	public void tick() {
		this.updateButtonStates();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int cx = this.width / 2;
		g.drawCenteredString(this.font, this.title, cx, 8, GOLD);
		g.drawCenteredString(this.font, statusLine(), cx, 21, GRAY);

		int y = 34 + 24;
		g.drawString(this.font, "Recent runs", this.left, y, WHITE);
		y += 12;
		if (this.recent.isEmpty()) {
			g.drawString(this.font, "No runs yet. Press a benchmark button above.", this.left, y, GRAY);
		}
		// Proportional font: fixed column x positions instead of padded strings.
		for (ReportIndex.Summary s : this.recent) {
			int color = s.spikes() == 0 ? GREEN : s.spikes() <= 5 ? YELLOW : RED;
			String gc = s.spikesWithGc() >= 0 ? " (" + s.spikesWithGc() + " GC)" : "";
			g.drawString(this.font, trim(s.label(), 14), this.left, y, WHITE);
			g.drawString(this.font, String.format(Locale.ROOT, "%.0f FPS", s.averageFps()), this.left + 80, y, WHITE);
			g.drawString(this.font, String.format(Locale.ROOT, "1%% low %.0f%s", s.low1AverageFps(), s.lowsReliable() ? "" : "*"),
					this.left + 130, y, GRAY);
			g.drawString(this.font, s.spikes() + " stutters" + gc, this.left + 200, y, color);
			y += 10;
		}

		y = 34 + 24 + 12 + RECENT_ROWS * 10 + 6 + 24;
		g.drawString(this.font, "FPS boost", this.left, y, WHITE);
		g.drawString(this.font, LowEndPreset.isApplied() ? "active" : "trades some visuals for FPS",
				this.left + 52, y, LowEndPreset.isApplied() ? GREEN : GRAY);
	}

	private String statusLine() {
		BenchmarkSession s = BenchmarkController.active();
		if (s != null) {
			return s.isWarmingUp() ? "Benchmark warming up..." : String.format(Locale.ROOT, "Recording: %.0f s", s.recordedSeconds(System.nanoTime()));
		}
		if (this.minecraft.level == null) {
			return "Join a world to run a benchmark";
		}
		return "* = 1% low unreliable (run too short)";
	}

	private static String trim(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}
}
