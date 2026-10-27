package io.github.nivium.stats;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The most recent stutters (frames far slower than normal) with a best-effort cause. Causes are resolved when
 * the log is read, because GC notifications arrive after the pause they describe. Render thread only.
 */
public final class StutterLog {
	/** A slow frame. {@code startUptimeMs} is JVM uptime in milliseconds. */
	public record Stutter(long startUptimeMs, long durationNanos) {
		public double durationMs() {
			return this.durationNanos / 1e6;
		}
	}

	/** A stutter with the GC pause time that overlapped it (0 if none) and a short cause label. */
	public record Attributed(Stutter stutter, long gcPauseMs, String cause) {
		public boolean isGc() {
			return this.gcPauseMs > 0;
		}
	}

	private final int capacity;
	private final ArrayDeque<Stutter> stutters;
	private long total;

	public StutterLog(int capacity) {
		this.capacity = capacity;
		this.stutters = new ArrayDeque<>(capacity);
	}

	public void record(Stutter stutter) {
		if (this.stutters.size() == this.capacity) {
			this.stutters.removeFirst();
		}
		this.stutters.addLast(stutter);
		this.total++;
	}

	/** Stutters since the game started (not limited by capacity). */
	public long total() {
		return this.total;
	}

	public List<Stutter> recent() {
		return List.copyOf(this.stutters);
	}

	/** Stutters that started at or after {@code sinceUptimeMs}. */
	public List<Stutter> since(long sinceUptimeMs) {
		List<Stutter> out = new ArrayList<>();
		for (Stutter s : this.stutters) {
			if (s.startUptimeMs() >= sinceUptimeMs) {
				out.add(s);
			}
		}
		return out;
	}

	/** Newest first, each labeled as a GC pause, partly GC, or something else. */
	public static List<Attributed> attribute(List<Stutter> stutters, List<GcEventLog.Event> gcEvents) {
		List<Attributed> out = new ArrayList<>(stutters.size());
		for (int i = stutters.size() - 1; i >= 0; i--) {
			Stutter s = stutters.get(i);
			long end = s.startUptimeMs() + Math.max(1, Math.round(s.durationMs()));
			long gc = GcEventLog.pauseMsOverlapping(gcEvents, s.startUptimeMs(), end);
			String cause;
			if (gc >= s.durationMs() * 0.5) {
				cause = "memory cleanup (GC pause " + gc + " ms)";
			} else if (gc > 0) {
				cause = "partly memory cleanup (GC " + gc + " ms)";
			} else {
				cause = "not GC (chunk loading, rendering or Windows)";
			}
			out.add(new Attributed(s, gc, cause));
		}
		return out;
	}
}
