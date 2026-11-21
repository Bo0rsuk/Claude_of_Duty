package com.claudeofduty.core.service;

import com.claudeofduty.core.alerting.Timeline;
import com.claudeofduty.core.api.ApiException;
import com.claudeofduty.core.api.Caller;
import com.claudeofduty.core.api.Page;
import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.Member;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Runbook;
import com.claudeofduty.core.model.Severity;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.model.TimelineEntry;
import com.claudeofduty.core.notify.NotificationDispatcher;
import com.claudeofduty.core.port.AlertPublisher;
import com.claudeofduty.core.port.AlertRepository;
import com.claudeofduty.core.port.Membership;
import com.claudeofduty.core.port.Repository;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reading alerts and the actions people take on them. Every action lands in the alert's timeline. */
public final class AlertService {

    private static final int DEFAULT_PAGE = 50;
    private static final int MAX_PAGE = 200;

    /** Null fields do not filter. */
    public record Filter(Alert.Status status, Severity severity, String ruleId) {

        boolean accepts(Alert alert) {
            return (status == null || status == alert.status())
                    && (severity == null || severity == alert.severity())
                    && (ruleId == null || ruleId.equals(alert.ruleId()));
        }
    }

    /** The alert's own fields plus the runbook it refers to. */
    public record Details(@JsonUnwrapped Alert alert, Runbook runbook) {
    }

    /** What a resend needs besides the alert itself. */
    public record Notifications(Repository<Rule> rules, Repository<Source> sources, NotificationDispatcher dispatcher) {
    }

    private final AlertRepository alerts;
    private final Repository<Runbook> runbooks;
    private final Membership membership;
    private final AlertPublisher publisher;
    private final Notifications notifications;
    private final Clock clock;

    public AlertService(AlertRepository alerts, Repository<Runbook> runbooks, Membership membership,
            AlertPublisher publisher, Notifications notifications, Clock clock) {
        this.alerts = alerts;
        this.runbooks = runbooks;
        this.membership = membership;
        this.publisher = publisher;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** The filter is applied to each page after it is read, so a page can hold fewer items than the limit. */
    public Page<Alert> list(Caller caller, Filter filter, Integer limit, String cursor) {
        int pageSize = limit == null ? DEFAULT_PAGE : Math.max(1, Math.min(limit, MAX_PAGE));
        Page<Alert> page = alerts.newest(caller.orgId(), pageSize, cursor);
        List<Alert> items = page.items().stream().filter(filter::accepts).map(Alert::withoutSampleEvent).toList();
        return new Page<>(items, page.nextCursor());
    }

    public Details get(Caller caller, String alertId) {
        Alert alert = require(caller, alertId);
        Runbook runbook = alert.runbookId() == null
                ? null
                : runbooks.find(caller.orgId(), alert.runbookId()).orElse(null);
        return new Details(alert, runbook);
    }

    public List<TimelineEntry> timeline(Caller caller, String alertId) {
        return alerts.timeline(require(caller, alertId).alertId());
    }

    public Alert acknowledge(Caller caller, String alertId) {
        Alert alert = require(caller, alertId);
        if (alert.status() != Alert.Status.OPEN) {
            throw ApiException.conflict("Alert is already " + alert.status().name().toLowerCase());
        }
        Instant now = clock.instant();
        return change(caller, alert, TimelineEntry.Type.ACKNOWLEDGED, Map.of(), now, changes(
                "status", Alert.Status.ACKNOWLEDGED, "acknowledgedBy", caller.userId(), "acknowledgedAt", now));
    }

    public Alert resolve(Caller caller, String alertId) {
        Alert alert = require(caller, alertId);
        if (alert.status() == Alert.Status.RESOLVED) {
            throw ApiException.conflict("Alert is already resolved");
        }
        Instant now = clock.instant();
        Alert resolved = change(caller, alert, TimelineEntry.Type.RESOLVED, Map.of(), now, changes(
                "status", Alert.Status.RESOLVED, "resolvedBy", caller.userId(), "resolvedAt", now));
        alerts.release(alert.fingerprint());
        return resolved;
    }

    public Alert assign(Caller caller, String alertId, String assigneeId) {
        Alert alert = require(caller, alertId);
        Member assignee = membership.members(caller.orgId()).stream()
                .filter(member -> member.userId().equals(assigneeId))
                .findFirst()
                .orElseThrow(() -> ApiException.invalid("No member " + assigneeId + " in this organization"));
        String assigneeName = assignee.name() != null ? assignee.name() : assignee.email();
        return change(caller, alert, TimelineEntry.Type.ASSIGNED,
                changes("assigneeId", assigneeId, "assigneeName", assigneeName), clock.instant(),
                changes("assigneeId", assigneeId));
    }

    public Alert addNote(Caller caller, String alertId, String text) {
        if (text == null || text.isBlank()) {
            throw ApiException.invalid("A note needs text");
        }
        Alert alert = require(caller, alertId);
        alerts.appendTimeline(Timeline.entry(
                alert.alertId(), TimelineEntry.Type.NOTE, caller.actor(), Map.of("text", text.strip()), clock.instant()));
        // Published so that open alert windows reload their timeline.
        publisher.publish(AlertPublisher.UPDATED, alert);
        return alert;
    }

    /**
     * Records which steps of the alert's runbook checklist are done, replacing the previous set,
     * so that everyone looking at the alert sees the same progress.
     */
    public Alert checkSteps(Caller caller, String alertId, List<Integer> checked) {
        if (checked == null || checked.stream().anyMatch(step -> step == null || step < 0)) {
            throw ApiException.invalid("checked must be a list of step positions, counted from 0");
        }
        Alert alert = require(caller, alertId);
        Alert updated = alerts.update(caller.orgId(), alert.alertId(),
                        changes("checkedSteps", checked.stream().distinct().sorted().toList()))
                .orElseThrow(() -> notFound(alertId));
        publisher.publish(AlertPublisher.UPDATED, updated);
        return updated;
    }

    /** Removes an alert with its history. The next matching event opens a new alert. */
    public void delete(Caller caller, String alertId) {
        Alert alert = require(caller, alertId);
        alerts.delete(caller.orgId(), alert.alertId());
        if (alert.status() != Alert.Status.RESOLVED) {
            alerts.release(alert.fingerprint());
        }
        publisher.publish(AlertPublisher.DELETED, alert);
    }

    /**
     * Notifies everyone in the rule's current routing again. Throttling does not
     * apply, because a person asked for it; the timeline records who did.
     */
    public Alert resend(Caller caller, String alertId) {
        Alert alert = require(caller, alertId);
        Rule rule = notifications.rules().find(caller.orgId(), alert.ruleId())
                .orElseThrow(() -> ApiException.conflict("The rule that created this alert no longer exists"));
        Source source = notifications.sources().find(caller.orgId(), alert.sourceId()).orElse(null);
        int queued = notifications.dispatcher().resend(alert, rule, source);
        alerts.appendTimeline(Timeline.entry(alert.alertId(), TimelineEntry.Type.NOTIFICATIONS_RESENT, caller.actor(),
                Map.of("notifications", queued), clock.instant()));
        publisher.publish(AlertPublisher.UPDATED, alert);
        return alert;
    }

    private Alert change(Caller caller, Alert alert, TimelineEntry.Type type, Map<String, Object> detail,
            Instant now, Map<String, Object> changes) {
        Alert updated = alerts.update(caller.orgId(), alert.alertId(), changes)
                .orElseThrow(() -> notFound(alert.alertId()));
        alerts.appendTimeline(Timeline.entry(alert.alertId(), type, caller.actor(), detail, now));
        publisher.publish(AlertPublisher.UPDATED, updated);
        return updated;
    }

    private Alert require(Caller caller, String alertId) {
        return alerts.find(caller.orgId(), alertId).orElseThrow(() -> notFound(alertId));
    }

    private static ApiException notFound(String alertId) {
        return ApiException.notFound("Alert " + alertId + " does not exist");
    }

    private static Map<String, Object> changes(Object... keysAndValues) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            changes.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return changes;
    }
}
