package io.github.vortexoptimizer.client.hud;

/**
 * Rolling FPS / frame-time readout for the overlay. Averages over half-second windows so the numbers
 * are readable, and keeps the worst frame of the window because stutter is what players notice.
 * Render thread only; does nothing unless {@link #enabled} is set.
 */
public final class FrameMeter {
	private static final long WINDOW_NANOS = 500_000_000L;

	public static boolean enabled;

	private static long lastFrameStart = -1;
	private static long windowStart = -1;
	private static int windowFrames;
	private static long windowWorst;

	private static double fps;
	private static double avgMs;
	private static double worstMs;

	private FrameMeter() {
	}

	public static void onFrameStart() {
		if (!enabled) {
			lastFrameStart = -1;
			return;
		}
		long now = System.nanoTime();
		if (lastFrameStart < 0) {
			lastFrameStart = now;
			windowStart = now;
			return;
		}
		long frame = now - lastFrameStart;
		lastFrameStart = now;
		windowFrames++;
		windowWorst = Math.max(windowWorst, frame);
		long elapsed = now - windowStart;
		if (elapsed >= WINDOW_NANOS) {
			fps = windowFrames / (elapsed / 1e9);
			avgMs = elapsed / 1e6 / windowFrames;
			worstMs = windowWorst / 1e6;
			windowStart = now;
			windowFrames = 0;
			windowWorst = 0;
		}
	}

	public static double fps() {
		return fps;
	}

	public static double avgMs() {
		return avgMs;
	}

	public static double worstMs() {
		return worstMs;
	}
}
