package io.github.nivium.stats;

/**
 * Converts {@link System#nanoTime()} to JVM uptime milliseconds, the clock GC notifications use, without
 * calling the management bean every frame.
 */
public final class UptimeClock {
	private static final long ANCHOR_NANOS = System.nanoTime();
	private static final long ANCHOR_UPTIME_MS = JvmProbe.uptimeMillis();

	private UptimeClock() {
	}

	public static long uptimeAt(long nanos) {
		return ANCHOR_UPTIME_MS + (nanos - ANCHOR_NANOS) / 1_000_000L;
	}

	public static long now() {
		return uptimeAt(System.nanoTime());
	}
}
