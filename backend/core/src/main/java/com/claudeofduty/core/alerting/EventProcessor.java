package com.claudeofduty.core.alerting;

import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.RawRecord;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.model.TimelineEntry;
import com.claudeofduty.core.parser.EventNormalizer;
import com.claudeofduty.core.port.AlertNotifier;
import com.claudeofduty.core.port.AlertPublisher;
import com.claudeofduty.core.port.AlertStore;
import com.claudeofduty.core.port.RecentEvents;
import com.claudeofduty.core.port.RuleLookup;
import com.claudeofduty.core.port.SourceLookup;
import com.claudeofduty.core.rules.RuleMatcher;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The pipeline for one batch of raw records:
 * parse -> match rules -> group by fingerprint -> create or update one alert per group -> notify.
 *
 * Grouping happens before any write, so a burst of identical events in a batch
 * costs one write, not one per event. Groups are independent of each other, so
 * they are stored concurrently.
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

    /** Everything the pipeline reads from and writes to. */
    public record Ports(
            SourceLookup sources,
            RuleLookup rules,
            AlertStore alerts,
            AlertPublisher publisher,
            AlertNotifier notifier,
            RecentEvents recentEvents) {
    }

    private final Ports ports;
    private final EventNormalizer normalizer;
    private final RuleMatcher matcher;
    private final AlertFactory factory;
    private final RecentEventSampler sampler;
    private final Clock clock;
    private final Executor executor;

    public EventProcessor(Ports ports, EventNormalizer normalizer, RuleMatcher matcher, String appUrl, Clock clock) {
        this(ports, normalizer, matcher, appUrl, clock, Runnable::run);
    }

    /** @param executor runs the storing of each alert group; the ports must then be safe to call from its threads */
    public EventProcessor(Ports ports, EventNormalizer normalizer, RuleMatcher matcher, String appUrl, Clock clock,
            Executor executor) {
        this.ports = ports;
        this.normalizer = normalizer;
        this.matcher = matcher;
        this.factory = new AlertFactory(appUrl);
        this.sampler = new RecentEventSampler(clock);
        this.clock = clock;
        this.executor = executor;
    }

    public Summary process(List<RawRecord> records) {
        Map<String, Group> groups = new LinkedHashMap<>();
        List<NormalizedEvent> sample = new ArrayList<>();
        int events = 0;
        int matches = 0;

        for (RawRecord record : records) {
            Optional<Source> source = ports.sources().find(record.orgId(), record.sourceId()).filter(Source::enabled);
            if (source.isEmpty()) {
                continue;
            }
            List<Rule> sourceRules = ports.rules().enabledRulesFor(record.orgId(), record.sourceId());
            for (NormalizedEvent event : normalizer.normalize(record, source.get().parser())) {
                events++;
                if (sampler.take(event)) {
                    sample.add(event);
                }
                for (Rule rule : sourceRules) {
                    if (matcher.matches(rule, event)) {
                        matches++;
                        groups.computeIfAbsent(Fingerprint.of(rule, event), key -> new Group(rule, source.get(), event))
                                .add(event);
                    }
                }
            }
        }

        List<CompletableFuture<Boolean>> stored = groups.entrySet().stream()
                .map(entry -> CompletableFuture.supplyAsync(() -> store(entry.getKey(), entry.getValue()), executor))
                .toList();
        // join() rethrows a failure of any group, which fails the batch so that the stream retries it.
        int created = (int) stored.stream().map(CompletableFuture::join).filter(wasCreated -> wasCreated).count();
        saveSample(sample);
        return new Summary(records.size(), events, matches, created, groups.size() - created);
    }

    /** The sample only feeds previews, so failing to save it must not fail the batch. */
    private void saveSample(List<NormalizedEvent> sample) {
        if (sample.isEmpty()) {
            return;
        }
        try {
            ports.recentEvents().saveAll(sample);
        } catch (RuntimeException e) {
            System.err.println("Could not save the recent-events sample: " + e);
        }
    }

    /** @return true when a new alert was created, false when an existing one absorbed the events */
    private boolean store(String fingerprint, Group group) {
        Instant now = clock.instant();
        String candidateId = AlertFactory.newAlertId(now);
        String alertId = ports.alerts().claim(group.rule.orgId(), fingerprint, candidateId);
        boolean claimedByThisBatch = alertId.equals(candidateId);

        // The same write for both cases. Whoever claimed the fingerprint may not have
        // stored the alert yet when another batch arrives with more of its events.
        Alert alert = ports.alerts().addEvents(factory.create(
                alertId, fingerprint, group.rule, group.source, group.first, group.count, group.lastSeenAt, now));

        if (!claimedByThisBatch) {
            ports.publisher().publish(AlertPublisher.UPDATED, alert);
            return false;
        }
        ports.alerts().appendTimeline(Timeline.entry(alert.alertId(), TimelineEntry.Type.CREATED, TimelineEntry.Actor.system(),
                Map.of("ruleName", alert.ruleName(), "severity", alert.severity().name()), now));
        ports.publisher().publish(AlertPublisher.CREATED, alert);
        // Only a new alert notifies people. Events absorbed by an existing alert just raise its count.
        ports.notifier().alertCreated(alert, group.rule, group.source);
        return true;
    }
}
