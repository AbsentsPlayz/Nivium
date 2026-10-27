package io.github.nivium.client.gui;

import io.github.nivium.client.NiviumClientConfig;
import io.github.nivium.client.hud.FrameMeter;
import io.github.nivium.client.lag.LagMonitor;
import io.github.nivium.client.perf.LowEndPreset;
import io.github.nivium.lag.LagVerdict;
import io.github.nivium.stats.StutterLog;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Nivium screen (default key: K). Top: the lag check ("is it my PC, the server or my connection?") with live
 * numbers. Buttons: lag details, recommendations, recent freezes, plus the FPS overlay and the FPS boost preset.
 */
public final class NiviumScreen extends Screen {
	private static final int PANEL_WIDTH = 320;
	private static final int LINE = 10;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int AQUA = 0xFF55FFFF;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREEN = 0xFF55FF55;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int RED = 0xFFFF5555;
	private static final int VERDICT_Y = 30;
	private static final int STATS_TOP = 44;
	private static final int STATS_LINES = 4;
	private static final int BUTTON_ROW = STATS_TOP + STATS_LINES * LINE + 4;
	private static final int RESULT_TOP = BUTTON_ROW + 26;

	private final @Nullable Screen parent;
	private int left;
	private int resultBottom;
	private List<Component> result = List.of(Component.literal("Press a button above. Results appear here.").withStyle(ChatFormatting.GRAY));
	private List<FormattedCharSequence> wrapped = List.of();
	private int scroll;

	public NiviumScreen(@Nullable Screen parent) {
		super(Component.literal("Nivium"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.left = (this.width - PANEL_WIDTH) / 2;
		this.resultBottom = this.height - 30;
		this.rewrap();

		boolean inWorld = this.minecraft.level != null;
		List<Button> row = new ArrayList<>();
		row.add(this.showButton("Lag details", "What is slowing you down right now, and why.", this::lagDetails));
		row.add(this.showButton("Recommendations", "Tips for your PC and settings. Nothing is changed automatically.", this::recommendations));
		row.add(this.showButton("Recent freezes", "Freezes in the last minute, and whether memory cleanup (GC) caused them.", this::freezes));
		this.layoutRow(row, BUTTON_ROW);
		if (!inWorld) {
			row.get(0).active = false;
			row.get(2).active = false;
		}

		List<Button> bottom = new ArrayList<>();
		bottom.add(Button.builder(overlayLabel(), b -> {
			NiviumClientConfig c = NiviumClientConfig.get();
			c.fpsOverlay = !c.fpsOverlay;
			FrameMeter.enabled = c.fpsOverlay;
			c.save();
			b.setMessage(overlayLabel());
		}).tooltip(Tooltip.create(Component.literal("Shows FPS and frame time, plus server TPS and your ping."))).build());
		boolean applied = LowEndPreset.isApplied();
		Button preset = Button.builder(Component.literal(applied ? "Restore my video settings" : "FPS boost"), b -> {
			if (LowEndPreset.isApplied()) {
				LowEndPreset.restore(this.minecraft);
			} else {
				LowEndPreset.apply(this.minecraft);
			}
			this.rebuildWidgets();
		}).build();
		preset.setTooltip(Tooltip.create(Component.literal(applied
				? "Puts back the exact video settings you had before the FPS boost."
				: "Reversible. Lowers the settings that cost the most FPS:\n- " + String.join("\n- ", LowEndPreset.descriptions()))));
		bottom.add(preset);
		bottom.add(Button.builder(Component.literal("Done"), b -> this.onClose()).build());
		this.layoutRow(bottom, this.height - 24);
	}

	private Button showButton(String label, String tip, Supplier<List<Component>> content) {
		return Button.builder(Component.literal(label), b -> this.setResult(content.get()))
				.tooltip(Tooltip.create(Component.literal(tip))).build();
	}

	private List<Component> lagDetails() {
		LagVerdict.Result r = LagMonitor.verdict(this.minecraft);
		List<Component> out = new ArrayList<>();
		out.add(Component.literal(r.headline()).withStyle(sourceStyle(r.source()), ChatFormatting.BOLD));
		for (String f : r.findings()) {
			out.add(Component.literal("- " + f).withStyle(ChatFormatting.WHITE));
		}
		out.add(Component.literal("Server TPS is estimated from the server's time updates; it works on any server.")
				.withStyle(ChatFormatting.DARK_GRAY));
		return out;
	}

	private List<Component> recommendations() {
		List<Component> out = new ArrayList<>();
		out.add(Component.literal("Recommendations (nothing is changed automatically)").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		List<String> advice = LagMonitor.advice(this.minecraft);
		if (advice.isEmpty()) {
			out.add(Component.literal("Your setup looks good. Nothing to recommend.").withStyle(ChatFormatting.GREEN));
		}
		for (String a : advice) {
			out.add(Component.literal("- " + a).withStyle(ChatFormatting.WHITE));
		}
		return out;
	}

	private List<Component> freezes() {
		List<StutterLog.Attributed> recent = LagMonitor.recentStutters();
		List<Component> out = new ArrayList<>();
		out.add(Component.literal("Freezes in the last minute (frames over 100 ms)").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		if (recent.isEmpty()) {
			out.add(Component.literal("None. Smooth.").withStyle(ChatFormatting.GREEN));
		}
		for (StutterLog.Attributed a : recent) {
			out.add(Component.literal(String.format(Locale.ROOT, "%5.0f ms  ", a.stutter().durationMs())).withStyle(ChatFormatting.RED)
					.append(Component.literal(a.cause()).withStyle(a.isGc() ? ChatFormatting.YELLOW : ChatFormatting.WHITE)));
		}
		if (recent.stream().anyMatch(StutterLog.Attributed::isGc)) {
			out.add(Component.literal("Memory-cleanup freezes get shorter when Windows has more free RAM: close other programs.")
					.withStyle(ChatFormatting.GRAY));
		}
		return out;
	}

	/** Places buttons side by side, each as wide as its label (+ padding), centred, and adds them. */
	private void layoutRow(List<Button> buttons, int y) {
		int gap = 4;
		int total = -gap;
		int[] widths = new int[buttons.size()];
		for (int i = 0; i < buttons.size(); i++) {
			widths[i] = Math.max(60, this.font.width(buttons.get(i).getMessage()) + 16);
			total += widths[i] + gap;
		}
		int x = (this.width - total) / 2;
		for (int i = 0; i < buttons.size(); i++) {
			Button b = buttons.get(i);
			b.setRectangle(widths[i], 20, x, y);
			this.addRenderableWidget(b);
			x += widths[i] + gap;
		}
	}

	private void setResult(List<Component> lines) {
		this.result = lines;
		this.scroll = 0;
		this.rewrap();
	}

	private void rewrap() {
		List<FormattedCharSequence> out = new ArrayList<>();
		for (Component c : this.result) {
			out.addAll(this.font.split(c, PANEL_WIDTH - 8));
		}
		this.wrapped = out;
	}

	private int visibleLines() {
		return Math.max(1, (this.resultBottom - RESULT_TOP - 4) / LINE);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseY >= RESULT_TOP && mouseY <= this.resultBottom) {
			int max = Math.max(0, this.wrapped.size() - this.visibleLines());
			this.scroll = Math.max(0, Math.min(max, this.scroll - (int) Math.signum(scrollY)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private static Component overlayLabel() {
		return Component.literal("Overlay: " + (NiviumClientConfig.get().fpsOverlay ? "ON" : "OFF"));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int cx = this.width / 2;
		g.drawCenteredString(this.font, this.title, cx, 6, AQUA);
		g.drawCenteredString(this.font, "Smoother play on any server", cx, 17, GRAY);

		if (this.minecraft.level == null) {
			g.drawString(this.font, "Join a world or server to run the lag check.", this.left, VERDICT_Y, GRAY);
		} else {
			LagVerdict.Result r = LagMonitor.verdict(this.minecraft);
			g.drawString(this.font, Component.literal(r.headline()).withStyle(sourceStyle(r.source()), ChatFormatting.BOLD), this.left, VERDICT_Y, WHITE);
			int y = STATS_TOP;
			for (Line line : this.statLines()) {
				g.drawString(this.font, line.text(), this.left, y, line.color());
				y += LINE;
			}
		}

		g.fill(this.left - 2, RESULT_TOP - 2, this.left + PANEL_WIDTH + 2, this.resultBottom, 0x80000000);
		int visible = this.visibleLines();
		int y = RESULT_TOP + 2;
		for (int i = this.scroll; i < Math.min(this.wrapped.size(), this.scroll + visible); i++) {
			g.drawString(this.font, this.wrapped.get(i), this.left + 2, y, WHITE);
			y += LINE;
		}
		if (this.wrapped.size() > visible) {
			g.drawString(this.font, String.format(Locale.ROOT, "scroll %d/%d", this.scroll + 1, this.wrapped.size() - visible + 1),
					this.left + PANEL_WIDTH - 60, this.resultBottom - LINE, GRAY);
		}
	}

	private record Line(String text, int color) {
	}

	private List<Line> statLines() {
		List<Line> out = new ArrayList<>();
		double fps = FrameMeter.fps();
		out.add(new Line(String.format(Locale.ROOT, "Your PC:  %.0f FPS  (%.1f ms avg, worst %.0f ms)", fps, FrameMeter.avgMs(), FrameMeter.worstMs()),
				fps >= 60 ? GREEN : fps >= 30 ? YELLOW : RED));

		boolean local = this.minecraft.hasSingleplayerServer();
		double tps = LagMonitor.serverTps();
		double target = this.minecraft.level.tickRateManager().tickrate();
		String tpsText = Double.isNaN(tps) ? "measuring..." : String.format(Locale.ROOT, "%.1f / %.0f TPS", Math.min(tps, target), target);
		out.add(new Line((local ? "World:    " : "Server:   ") + tpsText,
				Double.isNaN(tps) ? GRAY : tps >= target * 0.9 ? GREEN : tps >= target * 0.6 ? YELLOW : RED));

		int ping = LagMonitor.pingMs(this.minecraft);
		out.add(local ? new Line("Ping:     none (singleplayer)", GRAY)
				: new Line("Ping:     " + (ping >= 0 ? ping + " ms" : "unknown"), ping < 0 ? GRAY : ping <= 80 ? GREEN : ping <= 150 ? YELLOW : RED));

		List<StutterLog.Attributed> recent = LagMonitor.recentStutters();
		long gc = recent.stream().filter(StutterLog.Attributed::isGc).count();
		out.add(new Line(String.format(Locale.ROOT, "Freezes:  %d in the last minute%s", recent.size(), gc > 0 ? " (" + gc + " memory cleanup)" : ""),
				recent.isEmpty() ? GREEN : recent.size() < 3 ? YELLOW : RED));
		return out;
	}

	private static ChatFormatting sourceStyle(LagVerdict.Source source) {
		return switch (source) {
			case NONE -> ChatFormatting.GREEN;
			case YOUR_PC -> ChatFormatting.YELLOW;
			case SERVER, CONNECTION -> ChatFormatting.RED;
		};
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}
}
