package io.github.vortexoptimizer.bench;

import com.sun.management.GarbageCollectionNotificationInfo;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Timeline of individual GC events during a capture, so frame spikes can be attributed to (or ruled
 * out as) collector pauses. Totals from {@link JvmProbe#gcSnapshot()} cannot do that.
 *
 * <p>Notifications arrive on a JMX thread; events are queued lock-free and read after the capture.
 * GC rates are low (a few per second at most), so the per-event allocation is irrelevant.
 */
public final class GcEventLog {
	/**
	 * @param startUptimeMs JVM uptime at which the event started (milliseconds)
	 * @param durationMs    event duration as reported by the collector; for G1 this is the pause time of
	 *                      young/mixed/full collections and of Remark/Cleanup for "G1 Concurrent GC"
	 */
	public record Event(long startUptimeMs, long durationMs, String collector, String action, String cause) {
	}

	private final ConcurrentLinkedQueue<Event> events = new ConcurrentLinkedQueue<>();
	private final List<NotificationEmitter> registered = new ArrayList<>();
	private final NotificationListener listener = this::onNotification;

	public void start() {
		for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
			if (bean instanceof NotificationEmitter emitter) {
				emitter.addNotificationListener(this.listener, null, null);
				this.registered.add(emitter);
			}
		}
	}

	public void stop() {
		for (NotificationEmitter emitter : this.registered) {
			try {
				emitter.removeNotificationListener(this.listener);
			} catch (ListenerNotFoundException ignored) {
			}
		}
		this.registered.clear();
	}

	public List<Event> events() {
		return List.copyOf(this.events);
	}

	private void onNotification(Notification notification, Object handback) {
		if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
			return;
		}
		GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
		this.events.add(new Event(info.getGcInfo().getStartTime(), info.getGcInfo().getDuration(),
				info.getGcName(), info.getGcAction(), info.getGcCause()));
	}

	/** A GC event placed on the frame timeline of a capture. */
	public record Placed(Event event, double offsetSeconds, int frameIndex, double frameMs) {
	}

	/**
	 * Places events on the frame timeline. Frame {@code i} spans
	 * {@code [sum(frames[0..i)), sum(frames[0..i]))} nanoseconds after the recording anchor.
	 * Events outside the recorded window are dropped.
	 *
	 * @param anchorUptimeMs JVM uptime at the instant recording started
	 */
	public static List<Placed> place(long[] frameNanos, long anchorUptimeMs, List<Event> events) {
		long[] ends = new long[frameNanos.length];
		long acc = 0;
		for (int i = 0; i < frameNanos.length; i++) {
			acc += frameNanos[i];
			ends[i] = acc;
		}
		List<Placed> out = new ArrayList<>();
		for (Event e : events) {
			long offsetNanos = (e.startUptimeMs() - anchorUptimeMs) * 1_000_000L;
			if (offsetNanos < 0 || offsetNanos >= acc) {
				continue;
			}
			int idx = java.util.Arrays.binarySearch(ends, offsetNanos);
			// binarySearch: exact hit means the event starts at the end of frame idx, i.e. in frame idx + 1.
			int frame = idx >= 0 ? idx + 1 : -idx - 1;
			frame = Math.min(frame, frameNanos.length - 1);
			out.add(new Placed(e, offsetNanos / 1e9, frame, frameNanos[frame] / 1e6));
		}
		return out;
	}
}
