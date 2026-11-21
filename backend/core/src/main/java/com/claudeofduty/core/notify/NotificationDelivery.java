package com.claudeofduty.core.notify;

import com.claudeofduty.core.alerting.Timeline;
import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.NotificationJob;
import com.claudeofduty.core.model.TimelineEntry;
import com.claudeofduty.core.port.NotificationQueue;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Delivers queued notifications and handles the ones that keep failing.
 *
 * A job is tried up to {@link #TRIES_PER_ROUND} times by its queue, then lands
 * in the dead-letter queue. From there it is sent back for another round, up
 * to {@link #MAX_ROUNDS} rounds in total. Every try is recorded in the alert timeline.
 */
public final class NotificationDelivery {

    public static final int TRIES_PER_ROUND = 3;
    public static final int MAX_ROUNDS = 3;
    private static final int RETRY_DELAY_SECONDS = 30;
    private static final int MAX_ERROR_LENGTH = 300;

    private final Map<String, NotificationChannel> channels;
    private final NotificationQueue queue;
    private final Consumer<TimelineEntry> timeline;
    private final Clock clock;

    public NotificationDelivery(Map<String, NotificationChannel> channels, NotificationQueue queue,
            Consumer<TimelineEntry> timeline, Clock clock) {
        this.channels = channels;
        this.queue = queue;
        this.timeline = timeline;
        this.clock = clock;
    }

    /**
     * @param receiveCount how many times the queue has handed out this job in the current round, starting at 1
     * @return false when the delivery failed and the queue should hand the job out again; true when it
     *         succeeded or failed for good
     */
    public boolean deliver(NotificationJob job, int receiveCount) {
        int tryNumber = (job.attempt() - 1) * TRIES_PER_ROUND + receiveCount;
        Map<String, Object> detail = detail(job, tryNumber);
        try {
            NotificationChannel channel = channels.get(job.channel());
            if (channel == null) {
                throw new IllegalStateException("No such channel: " + job.channel());
            }
            Map<String, Object> providerDetails = channel.send(job);
            Instant deliveredAt = clock.instant();
            detail.put("deliveredAt", Json.timestamp(deliveredAt));
            if (job.receivedAt() != null) {
                // Ingest-to-delivery latency, measured where it happens.
                detail.put("latencyMs", Duration.between(job.receivedAt(), deliveredAt).toMillis());
            }
            detail.putAll(providerDetails);
            record(job, TimelineEntry.Type.NOTIFICATION_SENT, detail);
            return true;
        } catch (PermanentDeliveryException e) {
            // Retrying cannot help, so the job is taken off the queue.
            detail.put("error", shorten(String.valueOf(e.getMessage())));
            detail.put("final", true);
            record(job, TimelineEntry.Type.NOTIFICATION_FAILED, detail);
            return true;
        } catch (RuntimeException e) {
            detail.put("error", shorten(String.valueOf(e.getMessage())));
            detail.put("final", false);
            record(job, TimelineEntry.Type.NOTIFICATION_FAILED, detail);
            return false;
        }
    }

    /** Called for a job that came out of the dead-letter queue. */
    public void retryOrGiveUp(NotificationJob job) {
        if (job.attempt() < MAX_ROUNDS) {
            queue.enqueue(job.withAttempt(job.attempt() + 1), RETRY_DELAY_SECONDS * job.attempt());
            return;
        }
        Map<String, Object> detail = detail(job, MAX_ROUNDS * TRIES_PER_ROUND);
        detail.put("error", "Gave up after " + MAX_ROUNDS * TRIES_PER_ROUND + " failed attempts");
        detail.put("final", true);
        record(job, TimelineEntry.Type.NOTIFICATION_FAILED, detail);
    }

    private static Map<String, Object> detail(NotificationJob job, int tryNumber) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("channel", job.channel());
        detail.put("recipient", job.recipient().address());
        detail.put("attempt", tryNumber);
        return detail;
    }

    private void record(NotificationJob job, TimelineEntry.Type type, Map<String, Object> detail) {
        timeline.accept(Timeline.entry(job.alertId(), type, TimelineEntry.Actor.system(), detail, clock.instant()));
    }

    private static String shorten(String text) {
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH) + "...";
    }
}
