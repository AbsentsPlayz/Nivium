package io.github.vortexoptimizer.bench;

/**
 * Fixed-capacity, allocation-free recorder of per-frame samples.
 *
 * <p>Only the render thread writes to a recorder. Each frame stores two primitives: the frame
 * duration in nanoseconds and the bytes allocated by the recording thread during that frame.
 * No boxing, no resizing, no allocation happens in {@link #record}, so the recorder itself does
 * not perturb the GC behaviour it is meant to observe.
 */
public final class FrameRecorder {
	private final long[] frameNanos;
	private final long[] allocatedBytes;
	private int count;
	private boolean overflowed;

	public FrameRecorder(int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity must be positive");
		}
		this.frameNanos = new long[capacity];
		this.allocatedBytes = new long[capacity];
	}

	/**
	 * Records one frame. When the recorder is full further frames are dropped and
	 * {@link #overflowed()} becomes true, so a report never silently mixes partial data.
	 *
	 * @param nanos frame duration in nanoseconds
	 * @param allocated bytes allocated by the render thread during the frame, or -1 if unknown
	 */
	public void record(long nanos, long allocated) {
		if (this.count == this.frameNanos.length) {
			this.overflowed = true;
			return;
		}
		this.frameNanos[this.count] = nanos;
		this.allocatedBytes[this.count] = allocated;
		this.count++;
	}

	public int count() {
		return this.count;
	}

	public int capacity() {
		return this.frameNanos.length;
	}

	public boolean overflowed() {
		return this.overflowed;
	}

	/** Returns a copy of the recorded frame durations; safe to sort. */
	public long[] frameNanosCopy() {
		return java.util.Arrays.copyOf(this.frameNanos, this.count);
	}

	/** Returns a copy of the recorded per-frame allocation byte counts. */
	public long[] allocatedBytesCopy() {
		return java.util.Arrays.copyOf(this.allocatedBytes, this.count);
	}
}
