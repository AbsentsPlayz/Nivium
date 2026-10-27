package io.github.nivium.lag;

/**
 * Estimates a remote server's TPS from the client side, without any mod on the server.
 *
 * <p>Vanilla servers send every player a time update every 20 server ticks, carrying the world's game time
 * (which advances exactly once per server tick). Game ticks gained divided by real seconds elapsed is the
 * server's tick rate. A 10 s window smooths out network jitter. Render thread only.
 */
public final class ServerTpsEstimator {
	private static final int CAPACITY = 32;
	private static final long WINDOW_NANOS = 10_000_000_000L;
	private static final long MIN_SPAN_NANOS = 3_000_000_000L;

	private final long[] nanos = new long[CAPACITY];
	private final long[] gameTimes = new long[CAPACITY];
	private int next;
	private int size;

	public void reset() {
		this.next = 0;
		this.size = 0;
	}

	/** Called for each time-update packet, on the render thread. */
	public void onTimeUpdate(long nowNanos, long gameTime) {
		if (this.size > 0 && gameTime < this.gameTimes[this.newest()]) {
			this.reset(); // went backwards: a different world or server
		}
		this.nanos[this.next] = nowNanos;
		this.gameTimes[this.next] = gameTime;
		this.next = (this.next + 1) % CAPACITY;
		if (this.size < CAPACITY) {
			this.size++;
		}
	}

	/** Estimated TPS over the last ~10 s, or NaN while there is not enough data yet. */
	public double tps() {
		if (this.size < 2) {
			return Double.NaN;
		}
		int newest = this.newest();
		long newestNanos = this.nanos[newest];
		int oldest = newest;
		for (int i = 1; i < this.size; i++) {
			int idx = Math.floorMod(newest - i, CAPACITY);
			if (newestNanos - this.nanos[idx] > WINDOW_NANOS) {
				break;
			}
			oldest = idx;
		}
		long span = newestNanos - this.nanos[oldest];
		if (span < MIN_SPAN_NANOS) {
			return Double.NaN;
		}
		return (this.gameTimes[newest] - this.gameTimes[oldest]) / (span / 1e9);
	}

	/** Seconds since the last time update, or NaN if none was received yet. */
	public double secondsSinceUpdate(long nowNanos) {
		return this.size == 0 ? Double.NaN : (nowNanos - this.nanos[this.newest()]) / 1e9;
	}

	private int newest() {
		return Math.floorMod(this.next - 1, CAPACITY);
	}
}
