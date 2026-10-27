package io.github.nivium.advice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Recommendations for the player's own setup. Never applied automatically; computed on demand.
 * Pure logic so every rule is unit-tested.
 */
public final class ClientAdvice {
	private static final double GIB = 1024.0 * 1024 * 1024;

	/**
	 * @param heapMaxBytes       Java -Xmx (what the launcher allocated)
	 * @param totalRamBytes      physical RAM, -1 if unknown
	 * @param freeRamBytes       free physical RAM right now, -1 if unknown
	 * @param recentGcStutters   freezes in the last minute caused by GC pauses
	 * @param recentStutters     all freezes in the last minute
	 */
	public record Inputs(long heapMaxBytes, long totalRamBytes, long freeRamBytes, int renderDistance, double fps,
						 int recentStutters, int recentGcStutters, boolean sodiumInstalled, boolean fpsPresetApplied) {
	}

	private ClientAdvice() {
	}

	public static List<String> compute(Inputs in) {
		List<String> out = new ArrayList<>();
		if (!in.sodiumInstalled()) {
			out.add("Install Sodium: it is the biggest FPS improvement for most PCs and works with Nivium.");
		}
		if (in.totalRamBytes() > 0 && in.freeRamBytes() >= 0 && in.freeRamBytes() < in.totalRamBytes() * 0.08) {
			out.add(String.format(Locale.ROOT, "Only %.1f of %.1f GB RAM is free. Close the browser, Discord or other programs: "
					+ "when Windows runs out of RAM, Minecraft freezes for whole seconds.", in.freeRamBytes() / GIB, in.totalRamBytes() / GIB));
		}
		if (in.totalRamBytes() > 0 && in.heapMaxBytes() > in.totalRamBytes() * 0.6) {
			out.add(String.format(Locale.ROOT, "Minecraft may use %.1f of your %.1f GB RAM. That leaves too little for Windows; "
					+ "set about 3-4 GB in your launcher instead.", in.heapMaxBytes() / GIB, in.totalRamBytes() / GIB));
		} else if (in.heapMaxBytes() > 0 && in.heapMaxBytes() < 2 * GIB) {
			out.add(String.format(Locale.ROOT, "Minecraft only has %.1f GB of memory. With mods, 3-4 GB is usual "
					+ "(launcher settings, if your PC has the RAM).", in.heapMaxBytes() / GIB));
		}
		if (in.recentGcStutters() >= 2) {
			out.add(in.recentGcStutters() + " of the last minute's freezes were memory cleanup (GC). Freeing system RAM "
					+ "(see above) usually helps more than anything in-game.");
		}
		if (in.renderDistance() > 12) {
			out.add("Render distance " + in.renderDistance() + ": on servers, 8-10 is usually plenty and much smoother.");
		}
		if (!in.fpsPresetApplied() && in.fps() > 0 && in.fps() < 60) {
			out.add(String.format(Locale.ROOT, "You are at %.0f FPS: try the Client FPS preset (button below). "
					+ "It is fully reversible.", in.fps()));
		}
		return out;
	}
}
