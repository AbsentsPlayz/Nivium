package io.github.nivium.lag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Answers the player's question "is the lag my PC, the server or my connection?" from numbers the client can
 * measure. Pure logic; thresholds are deliberately simple so the answer is explainable.
 */
public final class LagVerdict {
	public enum Source {
		NONE, YOUR_PC, SERVER, CONNECTION
	}

	/**
	 * @param fps                   frames per second over the last half second
	 * @param recentStutters        frames over 100 ms in the last 60 s
	 * @param recentGcStutters      of those, how many overlapped a GC pause
	 * @param serverTps             estimated server TPS, NaN if unknown
	 * @param targetTps             the server's intended tick rate (normally 20)
	 * @param secondsSinceUpdate    since the last time update from the server, NaN if none yet
	 * @param pingMs                latency the server reports for this player, -1 if unknown
	 * @param singleplayer          true when the "server" runs on this PC
	 */
	public record Inputs(double fps, int recentStutters, int recentGcStutters, double serverTps, double targetTps,
						 double secondsSinceUpdate, int pingMs, boolean singleplayer) {
	}

	/** {@code source} is the most likely main cause; {@code findings} lists everything noticed, worst first. */
	public record Result(Source source, String headline, List<String> findings) {
	}

	public static final double LOW_FPS = 30;
	public static final int STUTTERS_PER_MINUTE = 3;
	public static final double SERVER_TPS_RATIO = 0.9;
	public static final double SERVER_SILENT_SECONDS = 3;
	public static final int HIGH_PING_MS = 150;

	private LagVerdict() {
	}

	public static Result evaluate(Inputs in) {
		List<String> findings = new ArrayList<>();
		Source source = Source.NONE;
		String headline = null;
		String world = in.singleplayer() ? "World (runs on your PC)" : "Server";

		boolean silent = !in.singleplayer() && !Double.isNaN(in.secondsSinceUpdate()) && in.secondsSinceUpdate() > SERVER_SILENT_SECONDS;
		if (silent) {
			source = Source.SERVER;
			headline = String.format(Locale.ROOT, "Server is not responding (no update for %.0f s)", in.secondsSinceUpdate());
			findings.add(headline + ". The server or your connection froze, not your PC.");
		}

		boolean serverSlow = !Double.isNaN(in.serverTps()) && in.serverTps() < in.targetTps() * SERVER_TPS_RATIO;
		if (serverSlow) {
			String line = String.format(Locale.ROOT, "%s is lagging: %.1f / %.0f TPS. Blocks, mobs and redstone run slow for everyone.",
					world, in.serverTps(), in.targetTps());
			findings.add(line);
			if (source == Source.NONE) {
				source = Source.SERVER;
				headline = String.format(Locale.ROOT, "%s is lagging (%.1f TPS)", world, in.serverTps());
			}
		}

		boolean lowFps = in.fps() > 0 && in.fps() < LOW_FPS;
		boolean stuttering = in.recentStutters() >= STUTTERS_PER_MINUTE;
		if (lowFps || stuttering) {
			if (lowFps) {
				findings.add(String.format(Locale.ROOT, "Your PC: only %.0f FPS. Try the Client FPS preset or lower render distance.", in.fps()));
			}
			if (stuttering) {
				String gc = in.recentGcStutters() > 0
						? String.format(Locale.ROOT, " (%d were memory cleanup: free up RAM)", in.recentGcStutters()) : "";
				findings.add(String.format(Locale.ROOT, "Your PC: %d freezes in the last minute%s.", in.recentStutters(), gc));
			}
			if (source == Source.NONE) {
				source = Source.YOUR_PC;
				headline = lowFps ? String.format(Locale.ROOT, "Your PC is the bottleneck (%.0f FPS)", in.fps())
						: String.format(Locale.ROOT, "Your PC is stuttering (%d freezes/min)", in.recentStutters());
			}
		}

		boolean highPing = !in.singleplayer() && in.pingMs() > HIGH_PING_MS;
		if (highPing) {
			findings.add(String.format(Locale.ROOT, "Connection: %d ms ping. Actions reach the server late; that is your network, not your PC.", in.pingMs()));
			if (source == Source.NONE) {
				source = Source.CONNECTION;
				headline = String.format(Locale.ROOT, "High ping (%d ms)", in.pingMs());
			}
		}

		if (source == Source.NONE) {
			headline = "Everything looks smooth";
			findings.add("No lag detected: FPS, " + (in.singleplayer() ? "world TPS" : "server TPS and ping") + " look fine.");
		}
		return new Result(source, headline, findings);
	}
}
