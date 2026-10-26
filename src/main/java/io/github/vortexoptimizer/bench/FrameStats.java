package io.github.vortexoptimizer.bench;

import java.util.Arrays;

/**
 * Summary statistics over a set of frame durations.
 *
 * <p>Two "1% low" definitions are reported because tools disagree on the term:
 * <ul>
 *   <li>{@code low1PercentileFps}: 1000 / P99 frame time (the frame time 99% of frames beat).</li>
 *   <li>{@code low1AverageFps}: frames / total time of the slowest 1% of frames.</li>
 * </ul>
 * Both are only meaningful when enough frames were captured; {@link #reliableLows} is false
 * below {@link #MIN_FRAMES_FOR_LOWS} frames (fewer than 10 frames in the worst 0.1%).
 */
public record FrameStats(
		int frames,
		double totalSeconds,
		double averageFps,
		double meanFrameMs,
		double stdDevFrameMs,
		double p50FrameMs,
		double p95FrameMs,
		double p99FrameMs,
		double p999FrameMs,
		double maxFrameMs,
		double low1PercentileFps,
		double low1AverageFps,
		double low01AverageFps,
		int spikeFrames,
		double spikeThresholdMs,
		boolean reliableLows
) {
	public static final int MIN_FRAMES_FOR_LOWS = 10_000;

	/** A frame is a spike when it takes longer than this multiple of the median. */
	public static final double SPIKE_MEDIAN_MULTIPLIER = 2.0;

	/** ...and also longer than this absolute floor, so 0.4ms -> 0.9ms at 2000 FPS is not a spike. */
	public static final double SPIKE_FLOOR_MS = 8.0;

	public static FrameStats compute(long[] frameNanos) {
		int n = frameNanos.length;
		if (n == 0) {
			return new FrameStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);
		}

		long[] sorted = frameNanos.clone();
		Arrays.sort(sorted);

		double sumNanos = 0;
		for (long v : sorted) {
			sumNanos += v;
		}
		double meanNanos = sumNanos / n;
		double sq = 0;
		for (long v : sorted) {
			double d = v - meanNanos;
			sq += d * d;
		}
		double stdDevNanos = Math.sqrt(sq / n);

		double p50 = percentile(sorted, 50.0);
		double p99 = percentile(sorted, 99.0);
		double spikeThresholdNanos = Math.max(p50 * SPIKE_MEDIAN_MULTIPLIER, SPIKE_FLOOR_MS * 1e6);
		int spikes = 0;
		for (long v : frameNanos) {
			if (v > spikeThresholdNanos) {
				spikes++;
			}
		}

		return new FrameStats(
				n,
				sumNanos / 1e9,
				n / (sumNanos / 1e9),
				meanNanos / 1e6,
				stdDevNanos / 1e6,
				p50 / 1e6,
				percentile(sorted, 95.0) / 1e6,
				p99 / 1e6,
				percentile(sorted, 99.9) / 1e6,
				sorted[n - 1] / 1e6,
				p99 > 0 ? 1e9 / p99 : 0,
				worstFractionAverageFps(sorted, 0.01),
				worstFractionAverageFps(sorted, 0.001),
				spikes,
				spikeThresholdNanos / 1e6,
				n >= MIN_FRAMES_FOR_LOWS
		);
	}

	/** Linear-interpolated percentile (same method as numpy's default) over an ascending array. */
	static double percentile(long[] sorted, double p) {
		if (sorted.length == 1) {
			return sorted[0];
		}
		double rank = p / 100.0 * (sorted.length - 1);
		int lo = (int) Math.floor(rank);
		int hi = Math.min(lo + 1, sorted.length - 1);
		double frac = rank - lo;
		return sorted[lo] + (sorted[hi] - sorted[lo]) * frac;
	}

	/** Average FPS over the slowest {@code fraction} of frames (at least one frame). */
	static double worstFractionAverageFps(long[] sorted, double fraction) {
		int k = Math.max(1, (int) Math.ceil(sorted.length * fraction));
		double sum = 0;
		for (int i = sorted.length - k; i < sorted.length; i++) {
			sum += sorted[i];
		}
		return sum > 0 ? k / (sum / 1e9) : 0;
	}
}
