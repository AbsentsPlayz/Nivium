package io.github.nivium.monitor;

import io.github.nivium.stats.GcEventLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The most recent slow ticks, with a best-effort cause. Causes are resolved when the log is read because
 * GC notifications arrive after the pause they describe.
 */
public final class SpikeLog {
	/** A tick that took longer than the spike threshold. Times are JVM uptime in milliseconds. */
	public record Spike(int tick, long startUptimeMs, long durationNanos, boolean duringSave) {
		public double durationMs() {
			return this.durationNanos / 1e6;
		}
	}

	/** A spike with its likely cause. {@code gcPauseMs} is 0 when no GC pause overlapped it. */
	public record Attributed(Spike spike, long gcPauseMs, String cause) {
	}

	private final int capacity;
	private final ArrayDeque<Spike> spikes;
	private long total;

	public SpikeLog(int capacity) {
		this.capacity = capacity;
		this.spikes = new ArrayDeque<>(capacity);
	}

	public synchronized void record(Spike spike) {
		if (this.spikes.size() == this.capacity) {
			this.spikes.removeFirst();
		}
		this.spikes.addLast(spike);
		this.total++;
	}

	/** Spikes recorded since the monitor started (not limited by capacity). */
	public synchronized long total() {
		return this.total;
	}

	public synchronized List<Spike> recent() {
		return List.copyOf(this.spikes);
	}

	/** Newest first, each with the GC pause time that overlapped it and a short cause label. */
	public static List<Attributed> attribute(List<Spike> spikes, List<GcEventLog.Event> gcEvents) {
		List<Attributed> out = new ArrayList<>(spikes.size());
		for (int i = spikes.size() - 1; i >= 0; i--) {
			Spike s = spikes.get(i);
			long end = s.startUptimeMs() + Math.max(1, Math.round(s.durationMs()));
			long gc = GcEventLog.pauseMsOverlapping(gcEvents, s.startUptimeMs(), end);
			String cause;
			if (s.duringSave()) {
				cause = gc > 0 ? "world save + GC " + gc + " ms" : "world save";
			} else if (gc >= s.durationMs() * 0.5) {
				cause = "GC pause " + gc + " ms";
			} else if (gc > 0) {
				cause = "partly GC (" + gc + " ms)";
			} else {
				cause = "tick work (not GC, not saving)";
			}
			out.add(new Attributed(s, gc, cause));
		}
		return out;
	}
}
