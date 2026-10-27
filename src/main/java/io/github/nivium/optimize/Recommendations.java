package io.github.nivium.optimize;

import io.github.nivium.compat.ModCompat;
import io.github.nivium.stats.JvmProbe;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Advice for the admin. Nothing here is ever applied automatically; every line is shown as a recommendation.
 * Computed on demand (commands), never on a timer.
 */
public final class Recommendations {
	private static final double GIB = 1024.0 * 1024 * 1024;

	private Recommendations() {
	}

	public static List<String> compute(MinecraftServer server, int configuredView, int configuredSimulation) {
		List<String> out = new ArrayList<>();
		if (!ModCompat.LITHIUM) {
			out.add("Install Lithium: it optimizes general server tick work (entities, block ticking, collisions). Nivium does not duplicate it.");
		}
		if (ModCompat.SERVERCORE) {
			out.add("ServerCore is installed, so Nivium's adaptive distances stay off to avoid two mods adjusting the same settings.");
		}

		long heapMax = JvmProbe.heapMaxBytes();
		if (heapMax > 0 && heapMax < 3 * GIB) {
			out.add(String.format(Locale.ROOT, "The JVM may use at most %.1f GiB of heap. If the machine has RAM to spare, a larger -Xmx reduces GC pressure.", heapMax / GIB));
		}
		long total = JvmProbe.totalPhysicalMemoryBytes();
		long free = JvmProbe.freePhysicalMemoryBytes();
		if (total > 0 && free >= 0 && free < total * 0.08) {
			out.add(String.format(Locale.ROOT, "Only %.1f of %.1f GiB system RAM is free. If the OS starts swapping, GC pauses can take seconds instead of milliseconds.", free / GIB, total / GIB));
		}
		for (String name : JvmProbe.collectorNames()) {
			if (name.startsWith("PS ") || name.equals("Copy") || name.equals("MarkSweepCompact")) {
				out.add("The JVM uses the " + name + " collector, which pauses longer than G1. Java 21's default (G1) suits most servers.");
				break;
			}
		}

		if (server.isDedicatedServer()) {
			if (configuredView > 12) {
				out.add("view-distance=" + configuredView + " in server.properties: lowering it reduces chunk loading, memory and network work.");
			}
			if (configuredSimulation > 10) {
				out.add("simulation-distance=" + configuredSimulation + " in server.properties: every chunk in this radius around each player ticks. 6-10 is typical.");
			}
		}
		return out;
	}
}
