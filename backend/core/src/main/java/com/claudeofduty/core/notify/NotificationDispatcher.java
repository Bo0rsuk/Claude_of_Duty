package com.claudeofduty.core.notify;

import com.claudeofduty.core.alerting.Timeline;
import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.NotificationJob;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.model.TimelineEntry;
import com.claudeofduty.core.port.AlertNotifier;
import com.claudeofduty.core.port.NotificationQueue;
import java.time.Clock;
import java.util.Map;
import java.util.function.Consumer;

/** Queues the notifications planned for a new alert and records what was held back. */
public final class NotificationDispatcher implements AlertNotifier {

    private final NotificationPlanner planner;
    private final NotificationQueue queue;
    private final Consumer<TimelineEntry> timeline;
    private final Clock clock;

    public NotificationDispatcher(NotificationPlanner planner, NotificationQueue queue,
            Consumer<TimelineEntry> timeline, Clock clock) {
        this.planner = planner;
        this.queue = queue;
        this.timeline = timeline;
        this.clock = clock;
    }

    @Override
    public void alertCreated(Alert alert, Rule rule, Source source) {
        dispatch(alert, planner.plan(alert, rule, source));
    }

    /**
     * Notifies everyone in the rule's routing again, ignoring throttling.
     *
     * @return how many notifications were queued
     */
    public int resend(Alert alert, Rule rule, Source source) {
        NotificationPlanner.Plan plan = planner.plan(alert, rule, source, false);
        dispatch(alert, plan);
        return plan.jobs().size();
    }

    private void dispatch(Alert alert, NotificationPlanner.Plan plan) {

        for (NotificationPlanner.Suppressed suppressed : plan.suppressed()) {
            record(alert, TimelineEntry.Type.NOTIFICATION_SUPPRESSED, Map.of(
                    "channel", suppressed.channel(), "recipient", suppressed.recipient(), "reason", suppressed.reason()));
        }
        for (NotificationJob job : plan.jobs()) {
            try {
                queue.enqueue(job, 0);
            } catch (RuntimeException e) {
                // The alert exists already. Failing here would reprocess the whole
                // batch, so the lost notification is made visible in the timeline instead.
                record(alert, TimelineEntry.Type.NOTIFICATION_FAILED, Map.of(
                        "channel", job.channel(), "recipient", job.recipient().address(), "attempt", 0,
                        "error", "Could not be queued: " + e.getMessage(), "final", true));
            }
        }
    }

    private void record(Alert alert, TimelineEntry.Type type, Map<String, Object> detail) {
        timeline.accept(Timeline.entry(alert.alertId(), type, TimelineEntry.Actor.system(), detail, clock.instant()));
    }
}
