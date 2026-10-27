package io.github.nivium.monitor;

/**
 * Fixed-capacity ring of nanosecond durations. {@link #add} is allocation-free, so recording every server
 * tick costs a couple of array writes. Not thread-safe: written and read on the server thread only.
 */
public final class RollingTimes {
	private final long[] values;
	private int next;
	private int size;

	public RollingTimes(int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity must be positive");
		}
		this.values = new long[capacity];
	}

	public void add(long nanos) {
		this.values[this.next] = nanos;
		this.next = (this.next + 1) % this.values.length;
		if (this.size < this.values.length) {
			this.size++;
		}
	}

	public int size() {
		return this.size;
	}

	public void clear() {
		this.next = 0;
		this.size = 0;
	}

	/** The most recent {@code n} values (or fewer if not yet recorded), oldest first. */
	public long[] latest(int n) {
		int k = Math.min(n, this.size);
		long[] out = new long[k];
		int start = this.next - k;
		if (start < 0) {
			start += this.values.length;
		}
		for (int i = 0; i < k; i++) {
			out[i] = this.values[(start + i) % this.values.length];
		}
		return out;
	}

	/** Mean of the most recent {@code n} values in milliseconds; 0 when empty. Allocation-free. */
	public double meanMs(int n) {
		int k = Math.min(n, this.size);
		if (k == 0) {
			return 0;
		}
		long sum = 0;
		int idx = this.next;
		for (int i = 0; i < k; i++) {
			idx = idx == 0 ? this.values.length - 1 : idx - 1;
			sum += this.values[idx];
		}
		return sum / 1e6 / k;
	}
}
