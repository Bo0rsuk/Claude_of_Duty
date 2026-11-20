package com.claudeofduty.core.alerting;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.RawRecord;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.model.TimelineEntry;
import com.claudeofduty.core.parser.EventNormalizer;
import com.claudeofduty.core.port.AlertPublisher;
import com.claudeofduty.core.port.AlertStore;
import com.claudeofduty.core.port.RuleLookup;
import com.claudeofduty.core.port.SourceLookup;
import com.claudeofduty.core.rules.RuleMatcher;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The pipeline for one batch of raw records:
 * parse -> match rules -> group by fingerprint -> create or update one alert per group.
 *
 * Grouping happens before any write, so a burst of identical events in a batch
 * costs one write, not one per event.
 */
public final class EventProcessor {

    public record Summary(int records, int events, int matches, int alertsCreated, int alertsUpdated) {
    }

    /** Events of one batch that belong to the same alert. */
    private static final class Group {
        private final Rule rule;
        private final Source source;
        private final NormalizedEvent first;
        private long count;
        private Instant lastSeenAt;

        Group(Rule rule, Source source, NormalizedEvent first) {
            this.rule = rule;
            this.source = source;
            this.first = first;
            this.lastSeenAt = first.eventTime();
        }

        void add(NormalizedEvent event) {
            count++;
            if (event.eventTime().isAfter(lastSeenAt)) {
                lastSeenAt = event.eventTime();
            }
        }
    }

    private final SourceLookup sources;
    private final RuleLookup rules;
    private final AlertStore alerts;
    private final AlertPublisher publisher;
    private final EventNormalizer normalizer;
    private final RuleMatcher matcher;
    private final AlertFactory factory;
    private final Clock clock;

    public EventProcessor(SourceLookup sources, RuleLookup rules, AlertStore alerts, AlertPublisher publisher,
            EventNormalizer normalizer, RuleMatcher matcher, String appUrl, Clock clock) {
        this.sources = sources;
        this.rules = rules;
        this.alerts = alerts;
        this.publisher = publisher;
        this.normalizer = normalizer;
        this.matcher = matcher;
        this.factory = new AlertFactory(appUrl);
        this.clock = clock;
    }

    public Summary process(List<RawRecord> records) {
        Map<String, Group> groups = new LinkedHashMap<>();
        int events = 0;
        int matches = 0;

        for (RawRecord record : records) {
            Optional<Source> source = sources.find(record.orgId(), record.sourceId()).filter(Source::enabled);
            if (source.isEmpty()) {
                continue;
            }
            List<Rule> sourceRules = rules.enabledRulesFor(record.orgId(), record.sourceId());
            for (NormalizedEvent event : normalizer.normalize(record, source.get().parser())) {
                events++;
                for (Rule rule : sourceRules) {
                    if (matcher.matches(rule, event)) {
                        matches++;
                        groups.computeIfAbsent(Fingerprint.of(rule, event), key -> new Group(rule, source.get(), event))
                                .add(event);
                    }
                }
            }
        }

        int created = 0;
        for (Map.Entry<String, Group> entry : groups.entrySet()) {
            if (store(entry.getKey(), entry.getValue())) {
                created++;
            }
        }
        return new Summary(records.size(), events, matches, created, groups.size() - created);
    }

    /** @return true when a new alert was created, false when an existing one absorbed the events */
    private boolean store(String fingerprint, Group group) {
        Instant now = clock.instant();
        String candidateId = AlertFactory.newAlertId(now);
        String activeId = alerts.claim(group.rule.orgId(), fingerprint, candidateId);

        if (!activeId.equals(candidateId)) {
            Optional<Alert> updated = alerts.addEvents(group.rule.orgId(), activeId, group.count, group.lastSeenAt);
            if (updated.isPresent()) {
                publisher.publish(AlertPublisher.UPDATED, updated.get());
                return false;
            }
            // The fingerprint was claimed but the alert never got written, e.g. the
            // function was interrupted in between. Create it now under the claimed id.
        }

        Alert alert = factory.create(
                activeId, fingerprint, group.rule, group.source, group.first, group.count, group.lastSeenAt, now);
        alerts.create(alert);
        alerts.appendTimeline(createdEntry(alert, now));
        publisher.publish(AlertPublisher.CREATED, alert);
        return true;
    }

    private static TimelineEntry createdEntry(Alert alert, Instant now) {
        String entryId = Json.timestamp(now) + "#" + String.format("%04x", ThreadLocalRandom.current().nextInt(0x10000));
        return new TimelineEntry(alert.alertId(), entryId, now, TimelineEntry.Type.CREATED,
                TimelineEntry.Actor.system(),
                Map.of("ruleName", alert.ruleName(), "severity", alert.severity().name()));
    }
}
