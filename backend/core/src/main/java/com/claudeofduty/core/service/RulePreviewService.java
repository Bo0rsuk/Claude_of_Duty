package com.claudeofduty.core.service;

import com.claudeofduty.core.api.ApiException;
import com.claudeofduty.core.api.Caller;
import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Rule.Match;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.port.RecentEvents;
import com.claudeofduty.core.port.Repository;
import com.claudeofduty.core.rules.MatchValidator;
import com.claudeofduty.core.rules.Operators;
import com.claudeofduty.core.rules.RuleMatcher;
import java.util.List;

/** Tries a rule's conditions on the events a source produced lately, before the rule is saved. */
public final class RulePreviewService {

    public static final int PREVIEW_EVENTS = 100;
    private static final int MAX_EVENTS = 200;
    private static final int MAX_SAMPLES = 5;

    /** {@code samples} are a few of the matching events, newest first. */
    public record Preview(int matched, int total, List<NormalizedEvent> samples) {
    }

    private final Repository<Source> sources;
    private final RecentEvents recentEvents;
    private final Operators operators;
    private final RuleMatcher matcher;

    public RulePreviewService(Repository<Source> sources, RecentEvents recentEvents, Operators operators) {
        this.sources = sources;
        this.recentEvents = recentEvents;
        this.operators = operators;
        this.matcher = new RuleMatcher(operators);
    }

    public List<NormalizedEvent> events(Caller caller, String sourceId, Integer limit) {
        requireSource(caller, sourceId);
        int count = limit == null ? PREVIEW_EVENTS : Math.max(1, Math.min(limit, MAX_EVENTS));
        return recentEvents.latest(caller.orgId(), sourceId, count);
    }

    public Preview preview(Caller caller, String sourceId, Match match) {
        try {
            MatchValidator.validate(match, operators);
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid(e.getMessage());
        }
        return preview(events(caller, sourceId, PREVIEW_EVENTS), match);
    }

    Preview preview(List<NormalizedEvent> events, Match match) {
        Rule rule = new Rule(null, null, null, null, true, match, null, null, null, null, null, null, null, null, null, null);
        List<NormalizedEvent> matching = events.stream().filter(event -> matcher.matches(rule, event)).toList();
        return new Preview(matching.size(), events.size(), matching.stream().limit(MAX_SAMPLES).toList());
    }

    private void requireSource(Caller caller, String sourceId) {
        if (sourceId == null || sources.find(caller.orgId(), sourceId).isEmpty()) {
            throw ApiException.notFound("Source " + sourceId + " does not exist");
        }
    }
}
