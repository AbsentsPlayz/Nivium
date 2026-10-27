package io.github.nivium.client.hud;

import io.github.nivium.client.lag.LagMonitor;
import io.github.nivium.stats.StutterLog;
import io.github.nivium.stats.UptimeClock;
import net.minecraft.client.Minecraft;

/**
 * Measures every frame: rolling FPS / frame time over half-second windows (for the overlay and the lag check)
 * and stutter detection. Cost per frame: one {@code nanoTime()} and a few arithmetic operations. Render thread only.
 *
 * <p>Stutters are only recorded while actually playing (in a world, window focused, no menu open), so loading
 * screens and the "unfocused window" FPS limit are not mistaken for freezes.
 */
public final class FrameMeter {
	private static final long WINDOW_NANOS = 500_000_000L;
	/** A frame slower than this is a stutter (a visible freeze). */
	public static final long STUTTER_NANOS = 100_000_000L;

	/** Whether the FPS overlay is shown. Measurement runs regardless; it is what the lag check uses. */
	public static boolean enabled;

	private static long lastFrameStart = -1;
	private static long windowStart = -1;
	private static int windowFrames;
	private static long windowWorst;
	private static boolean lastFrameCounted;

	private static double fps;
	private static double avgMs;
	private static double worstMs;

	private FrameMeter() {
	}

	public static void onFrameStart() {
		long now = System.nanoTime();
		Minecraft mc = Minecraft.getInstance();
		boolean playing = mc.level != null && mc.screen == null && mc.isWindowActive();
		if (lastFrameStart < 0) {
			lastFrameStart = now;
			windowStart = now;
			lastFrameCounted = playing;
			return;
		}
		long frame = now - lastFrameStart;
		if (frame > STUTTER_NANOS && playing && lastFrameCounted) {
			LagMonitor.STUTTERS.record(new StutterLog.Stutter(UptimeClock.uptimeAt(lastFrameStart), frame));
		}
		lastFrameStart = now;
		lastFrameCounted = playing;

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
