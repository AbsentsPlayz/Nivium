package io.github.nivium.stats;

import java.util.Arrays;

/**
 * Summary statistics over a set of durations (server tick times, frame times).
 *
 * <p>A sample counts as a spike when it is longer than {@code max(spikeMultiplier * median, spikeFloorMs)}:
 * the multiplier catches outliers relative to normal load, the floor stops tiny absolute changes from counting.
 */
public record TimingStats(
		int count,
		double totalSeconds,
		double meanMs,
		double stdDevMs,
		double p50Ms,
		double p95Ms,
		double p99Ms,
		double maxMs,
		int spikes,
		double spikeThresholdMs
) {
	public static final TimingStats EMPTY = new TimingStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

	public static TimingStats compute(long[] nanos, double spikeMultiplier, double spikeFloorMs) {
		int n = nanos.length;
		if (n == 0) {
			return EMPTY;
		}

		long[] sorted = nanos.clone();
		Arrays.sort(sorted);

		double sum = 0;
		for (long v : sorted) {
			sum += v;
		}
		double mean = sum / n;
		double sq = 0;
		for (long v : sorted) {
			double d = v - mean;
			sq += d * d;
		}

		double p50 = percentile(sorted, 50.0);
		double threshold = Math.max(p50 * spikeMultiplier, spikeFloorMs * 1e6);
		int spikes = 0;
		for (long v : sorted) {
			if (v > threshold) {
				spikes++;
			}
		}

		return new TimingStats(
				n,
				sum / 1e9,
				mean / 1e6,
				Math.sqrt(sq / n) / 1e6,
				p50 / 1e6,
				percentile(sorted, 95.0) / 1e6,
				percentile(sorted, 99.0) / 1e6,
				sorted[n - 1] / 1e6,
				spikes,
				threshold / 1e6
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
}
