package io.github.nivium.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.github.nivium.Nivium;
import io.github.nivium.config.NiviumConfig;
import io.github.nivium.monitor.PerformanceSnapshot;
import io.github.nivium.monitor.SpikeLog;
import io.github.nivium.monitor.TickMonitor;
import io.github.nivium.optimize.AdaptiveDistances;
import io.github.nivium.stats.TimingStats;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Locale;

/** {@code /nivium status|spikes|recommendations|adaptive|reload}, for operators (permission level 2). */
public final class NiviumCommands {
	private static final double MIB = 1024.0 * 1024;

	private NiviumCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("nivium")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(NiviumCommands::status)
				.then(Commands.literal("status").executes(NiviumCommands::status))
				.then(Commands.literal("spikes").executes(NiviumCommands::spikes))
				.then(Commands.literal("recommendations").executes(NiviumCommands::recommendations))
				.then(Commands.literal("adaptive")
						.executes(NiviumCommands::adaptiveInfo)
						.then(Commands.literal("on").executes(ctx -> setAdaptive(ctx, true)))
						.then(Commands.literal("off").executes(ctx -> setAdaptive(ctx, false))))
				.then(Commands.literal("reload").executes(NiviumCommands::reload)));
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		TickMonitor m = Nivium.monitor();
		PerformanceSnapshot s = m != null ? m.snapshot() : null;
		if (s == null) {
			ctx.getSource().sendFailure(Component.literal("Nivium is still collecting its first second of data."));
			return 0;
		}
		send(ctx, header("Nivium server status"));
		send(ctx, line("TPS", String.format(Locale.ROOT, "%.1f / %.0f", s.tps(), s.targetTps()), tpsColor(s.tps(), s.targetTps()))
				.append(s.sprinting() ? Component.literal("  (tick sprint)").withStyle(ChatFormatting.GRAY) : Component.empty())
				.append(s.frozen() ? Component.literal("  (frozen)").withStyle(ChatFormatting.GRAY) : Component.empty()));
		send(ctx, line("MSPT 5 s", mspt(s.last5s()), msptColor(s.last5s().meanMs())));
		send(ctx, line("MSPT 60 s", mspt(s.last60s()), msptColor(s.last60s().meanMs())));
		send(ctx, line("Slow ticks", String.format(Locale.ROOT, "%d in the last 60 s, %d since start (> %.0f ms)",
				s.last60s().spikes(), s.totalSpikes(), s.spikeThresholdMs()), s.last60s().spikes() > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
		send(ctx, line("Memory", String.format(Locale.ROOT, "%.0f / %.0f MiB heap, %d GCs (%.1f s total)",
				s.heapUsedBytes() / MIB, s.heapMaxBytes() / MIB, s.gcCount(), s.gcTimeMs() / 1000.0), ChatFormatting.WHITE));
		send(ctx, line("World", s.loadedChunks() + " loaded chunks, " + s.players() + " players", ChatFormatting.WHITE));
		send(ctx, line("Simulation distance", distance(ctx.getSource().getServer(), s.simulation(), Nivium.config().adaptiveSimulationDistance), ChatFormatting.WHITE));
		send(ctx, line("View distance", distance(ctx.getSource().getServer(), s.view(), Nivium.config().adaptiveViewDistance), ChatFormatting.WHITE));
		send(ctx, Component.literal("/nivium spikes  /nivium recommendations").withStyle(ChatFormatting.DARK_GRAY));
		return 1;
	}

	private static int spikes(CommandContext<CommandSourceStack> ctx) {
		TickMonitor m = Nivium.monitor();
		if (m == null) {
			return 0;
		}
		List<SpikeLog.Attributed> list = SpikeLog.attribute(m.spikes().recent(), m.gcLog().recent());
		send(ctx, header("Recent slow ticks (> " + Math.round(Nivium.config().monitoring.spikeThresholdMs) + " ms)"));
		if (list.isEmpty()) {
			send(ctx, Component.literal("None recorded.").withStyle(ChatFormatting.GREEN));
			return 1;
		}
		int now = ctx.getSource().getServer().getTickCount();
		for (SpikeLog.Attributed a : list.subList(0, Math.min(10, list.size()))) {
			int agoSeconds = (now - a.spike().tick()) / 20;
			send(ctx, Component.literal(String.format(Locale.ROOT, "%6.0f ms", a.spike().durationMs())).withStyle(ChatFormatting.RED)
					.append(Component.literal(String.format(Locale.ROOT, "  %s ago  ", ago(agoSeconds))).withStyle(ChatFormatting.GRAY))
					.append(Component.literal(a.cause()).withStyle(ChatFormatting.WHITE)));
		}
		return 1;
	}

	private static int recommendations(CommandContext<CommandSourceStack> ctx) {
		NiviumActions.recommendations(ctx.getSource().getServer()).forEach(c -> send(ctx, c));
		return 1;
	}

	private static int adaptiveInfo(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		send(ctx, header("Adaptive distances"));
		send(ctx, Component.literal("Steps a distance down by 1 when mean tick time stays high, and back up when it stays low. "
				+ "Live only: server.properties is never changed, and a restart restores your values.").withStyle(ChatFormatting.GRAY));
		NiviumConfig c = Nivium.config();
		send(ctx, line("Simulation", explain(server, c.adaptiveSimulationDistance), ChatFormatting.WHITE));
		send(ctx, line("View", explain(server, c.adaptiveViewDistance), ChatFormatting.WHITE));
		return 1;
	}

	private static int setAdaptive(CommandContext<CommandSourceStack> ctx, boolean on) {
		NiviumActions.setAdaptive(ctx.getSource().getServer(), on).forEach(c -> send(ctx, c));
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		NiviumActions.reload(ctx.getSource().getServer()).forEach(c -> send(ctx, c));
		return 1;
	}

	private static String explain(MinecraftServer server, NiviumConfig.Adaptive a) {
		String blocked = AdaptiveDistances.blockedReason(server, a);
		if (blocked != null) {
			return "inactive (" + blocked + ")";
		}
		return String.format(Locale.ROOT, "active: down when mean >= %.0f ms for %.0f s, up when <= %.0f ms for %.0f s, min %d",
				a.overloadMspt, a.overloadSeconds, a.recoverMspt, a.recoverSeconds, a.minDistance);
	}

	private static String distance(MinecraftServer server, PerformanceSnapshot.DistanceState d, NiviumConfig.Adaptive config) {
		if (d.configured() < 0) {
			return "not evaluated yet";
		}
		if (d.reduced()) {
			return d.current() + " (APPLIED by Nivium; your value " + d.configured() + " returns when load drops)";
		}
		String blocked = AdaptiveDistances.blockedReason(server, config);
		return d.current() + (blocked != null ? "  (adaptive off: " + blocked + ")" : "  (adaptive on, not reduced)");
	}

	private static String mspt(TimingStats t) {
		return String.format(Locale.ROOT, "mean %.1f  p95 %.1f  max %.0f ms", t.meanMs(), t.p95Ms(), t.maxMs());
	}

	private static String ago(int seconds) {
		return seconds < 120 ? seconds + " s" : seconds / 60 + " min";
	}

	private static ChatFormatting tpsColor(double tps, double target) {
		return tps >= target * 0.95 ? ChatFormatting.GREEN : tps >= target * 0.75 ? ChatFormatting.YELLOW : ChatFormatting.RED;
	}

	private static ChatFormatting msptColor(double mean) {
		return mean < 35 ? ChatFormatting.GREEN : mean < 50 ? ChatFormatting.YELLOW : ChatFormatting.RED;
	}

	private static MutableComponent header(String text) {
		return Component.literal(text).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
	}

	private static MutableComponent line(String label, String value, ChatFormatting color) {
		return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(value).withStyle(color));
	}

	private static void send(CommandContext<CommandSourceStack> ctx, Component c) {
		ctx.getSource().sendSuccess(() -> c, false);
	}
}
