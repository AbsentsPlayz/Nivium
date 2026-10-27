package io.github.nivium.stats;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Thin wrapper over the JVM management beans. Every method degrades to a sentinel ({@code -1})
 * instead of throwing when the running JVM lacks the HotSpot extensions.
 */
public final class JvmProbe {
	private static final com.sun.management.OperatingSystemMXBean OS =
			ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean bean ? bean : null;

	private JvmProbe() {
	}

	/** CPU time consumed by the whole process in nanoseconds, or -1 when unsupported. */
	public static long processCpuNanos() {
		return OS != null ? OS.getProcessCpuTime() : -1;
	}

	/** Physical memory of the machine in bytes, or -1 when unsupported. */
	public static long totalPhysicalMemoryBytes() {
		return OS != null ? OS.getTotalMemorySize() : -1;
	}

	/** Free physical memory of the machine in bytes, or -1 when unsupported. */
	public static long freePhysicalMemoryBytes() {
		return OS != null ? OS.getFreeMemorySize() : -1;
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

	/** Total collections and accumulated collection time (ms) across all collectors. */
	public static long[] gcTotals() {
		long count = 0;
		long time = 0;
		for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
			count += Math.max(0, bean.getCollectionCount());
			time += Math.max(0, bean.getCollectionTime());
		}
		return new long[]{count, time};
	}

	public static List<String> collectorNames() {
		return ManagementFactory.getGarbageCollectorMXBeans().stream().map(GarbageCollectorMXBean::getName).toList();
	}

	/** JVM uptime in milliseconds; the clock GC notifications are timestamped with. */
	public static long uptimeMillis() {
		return ManagementFactory.getRuntimeMXBean().getUptime();
	}
}
