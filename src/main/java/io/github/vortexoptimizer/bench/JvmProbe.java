package io.github.vortexoptimizer.bench;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper over the JVM management beans used by benchmarks. Every method degrades to a
 * sentinel ({@code -1}) instead of throwing when the running JVM lacks the HotSpot extensions.
 */
public final class JvmProbe {
	private static final com.sun.management.ThreadMXBean THREADS = hotspotThreads();
	private static final com.sun.management.OperatingSystemMXBean OS = hotspotOs();

	private JvmProbe() {
	}

	private static com.sun.management.ThreadMXBean hotspotThreads() {
		try {
			if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
					&& bean.isThreadAllocatedMemorySupported()) {
				if (!bean.isThreadAllocatedMemoryEnabled()) {
					bean.setThreadAllocatedMemoryEnabled(true);
				}
				return bean;
			}
		} catch (UnsupportedOperationException | SecurityException ignored) {
		}
		return null;
	}

	private static com.sun.management.OperatingSystemMXBean hotspotOs() {
		return ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean bean
				? bean : null;
	}

	/** Bytes allocated so far by the calling thread, or -1 when unsupported. Allocation-free on HotSpot. */
	public static long currentThreadAllocatedBytes() {
		return THREADS != null ? THREADS.getCurrentThreadAllocatedBytes() : -1;
	}

	/** CPU time consumed by the whole process in nanoseconds, or -1 when unsupported. */
	public static long processCpuNanos() {
		return OS != null ? OS.getProcessCpuTime() : -1;
	}

	public static int availableProcessors() {
		return Runtime.getRuntime().availableProcessors();
	}

	public static long heapUsedBytes() {
		Runtime rt = Runtime.getRuntime();
		return rt.totalMemory() - rt.freeMemory();
	}

	public static long heapMaxBytes() {
		return Runtime.getRuntime().maxMemory();
	}

	/** Snapshot of collection count and accumulated time per collector. */
	public static Map<String, long[]> gcSnapshot() {
		List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();
		Map<String, long[]> out = new LinkedHashMap<>();
		for (GarbageCollectorMXBean bean : beans) {
			out.put(bean.getName(), new long[]{bean.getCollectionCount(), bean.getCollectionTime()});
		}
		return out;
	}

	/** JVM uptime in milliseconds; the clock GC notifications are timestamped with. */
	public static long uptimeMillis() {
		return ManagementFactory.getRuntimeMXBean().getUptime();
	}

	/** JVM input arguments, minus the dev-launch arguments that only carry local file paths. */
	public static List<String> jvmArguments() {
		return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
				.filter(a -> !a.startsWith("-Dfabric.dli."))
				.toList();
	}
}
