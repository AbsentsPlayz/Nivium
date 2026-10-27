package io.github.nivium.stats;

import com.sun.management.GarbageCollectionNotificationInfo;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Rolling log of the most recent GC events, so tick spikes can be attributed to (or ruled out as)
 * collector pauses after the fact. Totals from {@link JvmProbe#gcSnapshot()} cannot do that.
 *
 * <p>Notifications arrive on a JMX thread, usually shortly <em>after</em> the GC ends, so matching is done
 * when spikes are read, not when they are recorded. GC rates are a few per second at most, so the
 * per-event allocation and the lock are irrelevant to tick performance.
 */
public final class GcEventLog {
	/**
	 * @param startUptimeMs JVM uptime at which the event started (milliseconds)
	 * @param durationMs    duration reported by the collector. For G1 this is pause time for young/mixed/full
	 *                      collections; "G1 Concurrent GC" events are mostly concurrent work, not pauses
	 *                      (measured in-game: a 755 ms concurrent event fell inside a normal 13 ms frame).
	 */
	public record Event(long startUptimeMs, long durationMs, String collector, String action, String cause) {
		public long endUptimeMs() {
			return this.startUptimeMs + this.durationMs;
		}

		/** True for events that stop application threads. */
		public boolean isPause() {
			return !this.collector.contains("Concurrent");
		}
	}

	private final int capacity;
	private final ArrayDeque<Event> events;
	private final List<NotificationEmitter> registered = new ArrayList<>();
	private final NotificationListener listener = this::onNotification;

	public GcEventLog(int capacity) {
		this.capacity = capacity;
		this.events = new ArrayDeque<>(capacity);
	}

	public synchronized void start() {
		if (!this.registered.isEmpty()) {
			return;
		}
		for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
			if (bean instanceof NotificationEmitter emitter) {
				emitter.addNotificationListener(this.listener, null, null);
				this.registered.add(emitter);
			}
		}
	}

	public synchronized void stop() {
		for (NotificationEmitter emitter : this.registered) {
			try {
				emitter.removeNotificationListener(this.listener);
			} catch (ListenerNotFoundException ignored) {
			}
		}
		this.registered.clear();
	}

	public synchronized void add(Event event) {
		if (this.events.size() == this.capacity) {
			this.events.removeFirst();
		}
		this.events.addLast(event);
	}

	public synchronized List<Event> recent() {
		return List.copyOf(this.events);
	}

	/** Total pause time of GC pauses overlapping {@code [fromUptimeMs, toUptimeMs]}. */
	public static long pauseMsOverlapping(List<Event> events, long fromUptimeMs, long toUptimeMs) {
		long total = 0;
		for (Event e : events) {
			if (e.isPause() && e.startUptimeMs() <= toUptimeMs && e.endUptimeMs() >= fromUptimeMs) {
				total += e.durationMs();
			}
		}
		return total;
	}

	private void onNotification(Notification notification, Object handback) {
		if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
			return;
		}
		GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
		this.add(new Event(info.getGcInfo().getStartTime(), info.getGcInfo().getDuration(),
				info.getGcName(), info.getGcAction(), info.getGcCause()));
	}
}
