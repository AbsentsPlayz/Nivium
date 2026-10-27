package io.github.nivium.client.gui;

import io.github.nivium.Nivium;
import io.github.nivium.client.NiviumClientConfig;
import io.github.nivium.client.hud.FrameMeter;
import io.github.nivium.client.perf.LowEndPreset;
import io.github.nivium.command.NiviumActions;
import io.github.nivium.monitor.PerformanceSnapshot;
import io.github.nivium.monitor.TickMonitor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Nivium screen (default key: K): live server stats, buttons for {@code /nivium recommendations},
 * {@code adaptive on/off} and {@code reload}, and the optional client extras.
 *
 * <p>In singleplayer/LAN the buttons run the action directly on this client's integrated server (on its thread)
 * and show the result here. On a remote server they send the real command, so the server still checks operator
 * permission, and the reply appears in chat.
 */
public final class NiviumScreen extends Screen {
	private static final int PANEL_WIDTH = 320;
	private static final int LINE = 10;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int AQUA = 0xFF55FFFF;
	private static final int GREEN = 0xFF55FF55;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int RED = 0xFFFF5555;
	private static final int STATS_TOP = 30;
	private static final int STATS_LINES = 5;
	private static final int COMMAND_ROW = STATS_TOP + STATS_LINES * LINE + 4;
	private static final int RESULT_TOP = COMMAND_ROW + 26;

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

		// Row 1: the three /nivium commands, sized to their labels and centred.
		boolean inWorld = this.minecraft.level != null;
		boolean adaptiveOn = Nivium.config().adaptiveSimulationDistance.enabled;
		boolean local = this.minecraft.hasSingleplayerServer();
		List<Button> row = new ArrayList<>();
		row.add(this.commandButton("Recommendations", "nivium recommendations", NiviumActions::recommendations,
				"Advice for this server. Never applied automatically."));
		Button on = this.commandButton("Adaptive ON", "nivium adaptive on", s -> NiviumActions.setAdaptive(s, true),
				"Turn on adaptive simulation distance and save it to config/nivium.json.");
		Button off = this.commandButton("Adaptive OFF", "nivium adaptive off", s -> NiviumActions.setAdaptive(s, false),
				"Turn off adaptive simulation distance, restore your value and save the config.");
		if (local) {
			on.active = !adaptiveOn;
			off.active = adaptiveOn;
		}
		row.add(on);
		row.add(off);
		row.add(this.commandButton("Reload", "nivium reload", NiviumActions::reload,
				"Re-read config/nivium.json and apply it. No restart needed."));
		this.layoutRow(row, COMMAND_ROW);
		if (!inWorld) {
			row.forEach(b -> b.active = false);
		}

		// Bottom row: client extras and Done.
		List<Button> bottom = new ArrayList<>();
		bottom.add(Button.builder(overlayLabel(), b -> {
			NiviumClientConfig c = NiviumClientConfig.get();
			c.fpsOverlay = !c.fpsOverlay;
			FrameMeter.enabled = c.fpsOverlay;
			c.save();
			b.setMessage(overlayLabel());
		}).tooltip(Tooltip.create(Component.literal("Client-side: FPS, average frame time and the worst frame of the last half second."))).build());
		boolean applied = LowEndPreset.isApplied();
		Button preset = Button.builder(Component.literal(applied ? "Restore my video settings" : "Client FPS preset"), b -> {
			if (LowEndPreset.isApplied()) {
				LowEndPreset.restore(this.minecraft);
			} else {
				LowEndPreset.apply(this.minecraft);
			}
			this.rebuildWidgets();
		}).build();
		preset.setTooltip(Tooltip.create(Component.literal(applied
				? "Puts back the exact video settings you had before the preset."
				: "Client-side, reversible. Changes your own video settings:\n- " + String.join("\n- ", LowEndPreset.descriptions()))));
		bottom.add(preset);
		bottom.add(Button.builder(Component.literal("Done"), b -> this.onClose()).build());
		this.layoutRow(bottom, this.height - 24);
	}

	private Button commandButton(String label, String command, Function<MinecraftServer, List<Component>> action, String tip) {
		return Button.builder(Component.literal(label), b -> this.run(command, action))
				.tooltip(Tooltip.create(Component.literal(tip + "\nSame as /" + command + ".")))
				.build();
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

	private void run(String command, Function<MinecraftServer, List<Component>> action) {
		IntegratedServer server = this.minecraft.getSingleplayerServer();
		if (server != null) {
			this.setResult(List.of(Component.literal("Running...").withStyle(ChatFormatting.GRAY)));
			// Server state is only touched on the server thread; the result comes back to the render thread.
			server.submit(() -> action.apply(server)).whenComplete((lines, error) -> this.minecraft.execute(() -> {
				if (error != null) {
					this.setResult(List.of(Component.literal("Failed: " + error.getMessage()).withStyle(ChatFormatting.RED)));
				} else {
					this.setResult(lines);
				}
				this.rebuildWidgets(); // refresh Adaptive ON/OFF availability
			}));
		} else if (this.minecraft.player != null) {
			this.minecraft.player.connection.sendCommand(command);
			this.setResult(List.of(
					Component.literal("Sent /" + command + " to the server.").withStyle(ChatFormatting.GREEN),
					Component.literal("The reply is in chat (press T). It works if you are an operator and the server runs Nivium.")
							.withStyle(ChatFormatting.GRAY)));
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
		return Component.literal("FPS overlay: " + (NiviumClientConfig.get().fpsOverlay ? "ON" : "OFF"));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int cx = this.width / 2;
		g.drawCenteredString(this.font, this.title, cx, 6, AQUA);
		g.drawCenteredString(this.font, "Advanced Minecraft Server Optimization", cx, 17, GRAY);

		int y = STATS_TOP;
		for (Line line : this.serverLines()) {
			g.drawString(this.font, line.text(), this.left, y, line.color());
			y += LINE;
		}

		// Result box
		g.fill(this.left - 2, RESULT_TOP - 2, this.left + PANEL_WIDTH + 2, this.resultBottom, 0x80000000);
		int visible = this.visibleLines();
		y = RESULT_TOP + 2;
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

	private List<Line> serverLines() {
		List<Line> out = new ArrayList<>();
		if (this.minecraft.level == null) {
			out.add(new Line("Open a world to see its server stats.", GRAY));
			return out;
		}
		if (!this.minecraft.hasSingleplayerServer()) {
			out.add(new Line("Remote server: live stats are on the server (/nivium status).", GRAY));
			out.add(new Line("The buttons below send the commands; replies appear in chat.", GRAY));
			return out;
		}
		TickMonitor m = Nivium.monitor();
		PerformanceSnapshot s = m != null ? m.snapshot() : null;
		if (s == null) {
			out.add(new Line("Collecting data...", GRAY));
			return out;
		}
		int tpsColor = s.tps() >= s.targetTps() * 0.95 ? GREEN : s.tps() >= s.targetTps() * 0.75 ? YELLOW : RED;
		out.add(new Line(String.format(Locale.ROOT, "TPS %.1f / %.0f%s", s.tps(), s.targetTps(), s.frozen() ? "  (frozen)" : ""), tpsColor));
		out.add(new Line(String.format(Locale.ROOT, "Tick time 5 s:  mean %.1f  p95 %.1f  max %.0f ms",
				s.last5s().meanMs(), s.last5s().p95Ms(), s.last5s().maxMs()), msptColor(s.last5s().meanMs())));
		out.add(new Line(String.format(Locale.ROOT, "Tick time 60 s: mean %.1f  p95 %.1f  max %.0f ms",
				s.last60s().meanMs(), s.last60s().p95Ms(), s.last60s().maxMs()), msptColor(s.last60s().meanMs())));
		out.add(new Line(String.format(Locale.ROOT, "Slow ticks (> %.0f ms): %d in 60 s, %d total",
				s.spikeThresholdMs(), s.last60s().spikes(), s.totalSpikes()), s.last60s().spikes() > 0 ? YELLOW : GREEN));
		out.add(new Line(String.format(Locale.ROOT, "Heap %.0f / %.0f MiB, %d chunks, adaptive %s",
				s.heapUsedBytes() / 1048576.0, s.heapMaxBytes() / 1048576.0, s.loadedChunks(),
				Nivium.config().adaptiveSimulationDistance.enabled ? "ON" : "OFF"), WHITE));
		return out;
	}

	private static int msptColor(double mean) {
		return mean < 35 ? GREEN : mean < 50 ? YELLOW : RED;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}
}
