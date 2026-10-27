package io.github.nivium.command;

import io.github.nivium.Nivium;
import io.github.nivium.config.NiviumConfig;
import io.github.nivium.optimize.AdaptiveDistances;
import io.github.nivium.optimize.Recommendations;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * The actions behind {@code /nivium recommendations|adaptive on|off|reload}, returning their output as lines so
 * both the commands (chat/console) and the client K screen (singleplayer) show exactly the same thing.
 * Server thread only.
 */
public final class NiviumActions {
	private NiviumActions() {
	}

	public static List<Component> recommendations(MinecraftServer server) {
		AdaptiveDistances a = Nivium.adaptive();
		int view = a != null && a.view().configured() > 0 ? a.view().configured() : server.getPlayerList().getViewDistance();
		int sim = a != null && a.simulation().configured() > 0 ? a.simulation().configured() : server.getPlayerList().getSimulationDistance();
		List<String> recs = Recommendations.compute(server, view, sim);

		List<Component> out = new ArrayList<>();
		out.add(Component.literal("Recommendations (not applied automatically)").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		if (recs.isEmpty()) {
			out.add(Component.literal("Nothing to recommend.").withStyle(ChatFormatting.GREEN));
		}
		for (String r : recs) {
			out.add(Component.literal("- " + r).withStyle(ChatFormatting.WHITE));
		}
		out.add(Component.literal("Loaded chunks per dimension:").withStyle(ChatFormatting.GRAY));
		for (ServerLevel level : server.getAllLevels()) {
			out.add(Component.literal("  " + level.dimension().identifier() + ": " + level.getChunkSource().getLoadedChunksCount())
					.withStyle(ChatFormatting.GRAY));
		}
		return out;
	}

	/** Turns adaptive simulation distance on or off, saves the config (with backup) and applies it live. */
	public static List<Component> setAdaptive(MinecraftServer server, boolean on) {
		List<Component> out = new ArrayList<>();
		NiviumConfig c = Nivium.config();
		c.adaptiveSimulationDistance.enabled = on;
		boolean saved = true;
		try {
			c.save(Nivium.configPath());
		} catch (Exception e) {
			saved = false;
			out.add(Component.literal("Applied for this session, but could not save config: " + e.getMessage()).withStyle(ChatFormatting.RED));
		}
		Nivium.applyConfig(server);
		out.add(Component.literal("Adaptive simulation distance " + (on ? "enabled" : "disabled and restored to your value") + ".")
				.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		String blocked = AdaptiveDistances.blockedReason(server, c.adaptiveSimulationDistance);
		if (on && blocked != null) {
			out.add(Component.literal("It stays inactive here: " + blocked + ".").withStyle(ChatFormatting.GRAY));
		}
		if (saved) {
			out.add(Component.literal("Saved to config/nivium.json (previous version in nivium.json.bak). No restart needed.")
					.withStyle(ChatFormatting.DARK_GRAY));
		}
		return out;
	}

	/** Re-reads config/nivium.json and applies it live. */
	public static List<Component> reload(MinecraftServer server) {
		List<Component> out = new ArrayList<>();
		List<String> warnings = Nivium.reloadConfig();
		Nivium.applyConfig(server);
		out.add(Component.literal("Nivium config reloaded and applied. No restart needed.").withStyle(ChatFormatting.GREEN));
		for (String w : warnings) {
			out.add(Component.literal("Warning: " + w).withStyle(ChatFormatting.YELLOW));
		}
		return out;
	}
}
