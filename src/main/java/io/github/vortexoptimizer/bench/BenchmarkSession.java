package io.github.vortexoptimizer.bench;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One benchmark capture. Driven from the render thread: {@link #onFrameBoundary} is called once
 * per frame, at the same point in the frame each time, with a monotonic timestamp.
 *
 * <p>The first boundary after {@link #start} only establishes the baseline; frame N's duration
 * is the distance between boundary N and N+1, so it includes everything the frame waited on
 * (GPU sync, vsync, frame limiter). Optional warm-up frames are discarded before recording
 * so cold-start shader/chunk work can be measured separately from steady state.
 */
public final class BenchmarkSession {
	/** 2^18 frames: ~73 minutes at 60 FPS, ~4 MiB for both arrays. */
	public static final int DEFAULT_CAPACITY = 1 << 18;

	private final String label;
	private final FrameRecorder recorder;
	private final long durationNanos;
	private int warmupFramesRemaining;
	private final int warmupFrames;

	private long lastBoundaryNanos = -1;
	private long lastAllocatedBytes = -1;

	private long startWallMillis;
	private long recordStartNanos = -1;
	private long recordEndNanos = -1;
	private long startCpuNanos = -1;
	private long endCpuNanos = -1;
	private Map<String, long[]> startGc;
	private Map<String, long[]> endGc;
	private long startHeap;
	private long endHeap;
	private long peakHeap;
	private long nextHeapSampleNanos;
	private final GcEventLog gcLog = new GcEventLog();
	private long anchorUptimeMs;
	private boolean finished;

	/**
	 * @param durationNanos stop automatically after this much recorded time; {@code <= 0} for manual stop
	 * @param warmupFrames frames to discard before recording begins
	 */
	public BenchmarkSession(String label, long durationNanos, int warmupFrames, int capacity) {
		this.label = label;
		this.durationNanos = durationNanos;
		this.warmupFrames = Math.max(0, warmupFrames);
		this.warmupFramesRemaining = this.warmupFrames;
		this.recorder = new FrameRecorder(capacity);
		this.startWallMillis = System.currentTimeMillis();
	}

	/**
	 * Feeds one frame boundary.
	 *
	 * @return true while the session wants more frames; false once it has finished
	 */
	public boolean onFrameBoundary(long nowNanos) {
		if (this.finished) {
			return false;
		}
		long allocated = JvmProbe.currentThreadAllocatedBytes();

		if (this.lastBoundaryNanos < 0) {
			this.lastBoundaryNanos = nowNanos;
			this.lastAllocatedBytes = allocated;
			if (this.warmupFramesRemaining == 0) {
				this.beginRecording(nowNanos);
			}
			return true;
		}

		long frame = nowNanos - this.lastBoundaryNanos;
		long frameAlloc = allocated >= 0 && this.lastAllocatedBytes >= 0 ? allocated - this.lastAllocatedBytes : -1;
		this.lastBoundaryNanos = nowNanos;
		this.lastAllocatedBytes = allocated;

		if (this.recordStartNanos < 0) {
			if (--this.warmupFramesRemaining <= 0) {
				this.beginRecording(nowNanos);
			}
			return true;
		}

		this.recorder.record(frame, frameAlloc);

		if (nowNanos >= this.nextHeapSampleNanos) {
			this.peakHeap = Math.max(this.peakHeap, JvmProbe.heapUsedBytes());
			this.nextHeapSampleNanos = nowNanos + 250_000_000L;
		}

		if ((this.durationNanos > 0 && nowNanos - this.recordStartNanos >= this.durationNanos)
				|| this.recorder.overflowed()) {
			this.finish(nowNanos);
			return false;
		}
		return true;
	}

	private void beginRecording(long nowNanos) {
		this.recordStartNanos = nowNanos;
		this.startWallMillis = System.currentTimeMillis();
		this.startCpuNanos = JvmProbe.processCpuNanos();
		this.startGc = JvmProbe.gcSnapshot();
		this.startHeap = JvmProbe.heapUsedBytes();
		this.peakHeap = this.startHeap;
		this.nextHeapSampleNanos = nowNanos;
		this.anchorUptimeMs = JvmProbe.uptimeMillis();
		this.gcLog.start();
	}

	/** Ends the capture (idempotent). Safe to call during warm-up; the result then has zero frames. */
	public void finish(long nowNanos) {
		if (this.finished) {
			return;
		}
		this.finished = true;
		if (this.recordStartNanos < 0) {
			this.beginRecording(nowNanos);
		}
		this.recordEndNanos = nowNanos;
		this.gcLog.stop();
		this.endCpuNanos = JvmProbe.processCpuNanos();
		this.endGc = JvmProbe.gcSnapshot();
		this.endHeap = JvmProbe.heapUsedBytes();
		this.peakHeap = Math.max(this.peakHeap, this.endHeap);
	}

	public boolean isFinished() {
		return this.finished;
	}

	public boolean isWarmingUp() {
		return !this.finished && this.recordStartNanos < 0;
	}

	public String label() {
		return this.label;
	}

	public int recordedFrames() {
		return this.recorder.count();
	}

	public double recordedSeconds(long nowNanos) {
		if (this.recordStartNanos < 0) {
			return 0;
		}
		long end = this.finished ? this.recordEndNanos : nowNanos;
		return (end - this.recordStartNanos) / 1e9;
	}

	public FrameRecorder recorder() {
		return this.recorder;
	}

	/** Builds the result. Only valid after {@link #finish}. */
	public BenchmarkResult result(Map<String, Object> environment) {
		if (!this.finished) {
			throw new IllegalStateException("session not finished");
		}
		long[] frames = this.recorder.frameNanosCopy();
		long[] allocs = this.recorder.allocatedBytesCopy();
		FrameStats stats = FrameStats.compute(frames);

		double wallSeconds = (this.recordEndNanos - this.recordStartNanos) / 1e9;
		double cpuUtil = -1;
		if (this.startCpuNanos >= 0 && this.endCpuNanos >= 0 && wallSeconds > 0) {
			cpuUtil = (this.endCpuNanos - this.startCpuNanos) / 1e9 / wallSeconds / JvmProbe.availableProcessors();
		}

		long allocTotal = 0;
		boolean allocKnown = allocs.length > 0;
		for (long a : allocs) {
			if (a < 0) {
				allocKnown = false;
				break;
			}
			allocTotal += a;
		}

		Map<String, long[]> gcDelta = new LinkedHashMap<>();
		for (Map.Entry<String, long[]> e : this.endGc.entrySet()) {
			long[] before = this.startGc.getOrDefault(e.getKey(), new long[]{0, 0});
			gcDelta.put(e.getKey(), new long[]{e.getValue()[0] - before[0], e.getValue()[1] - before[1]});
		}

		return new BenchmarkResult(
				this.label,
				this.startWallMillis,
				this.warmupFrames,
				wallSeconds,
				stats,
				this.recorder.overflowed(),
				cpuUtil,
				allocKnown ? allocTotal : -1,
				gcDelta,
				GcEventLog.place(frames, this.anchorUptimeMs, this.gcLog.events()),
				this.startHeap,
				this.endHeap,
				this.peakHeap,
				JvmProbe.heapMaxBytes(),
				environment,
				frames,
				allocs
		);
	}
}
