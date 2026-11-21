package com.claudeofduty.core.alerting;

import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Rule.Presentation;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.rules.EventFields;
import com.claudeofduty.core.template.Templates;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds an alert from the first event of a group, rendering the rule's presentation. */
final class AlertFactory {

    private final String appUrl;

    AlertFactory(String appUrl) {
        this.appUrl = appUrl;
    }

    /**
     * Starts with the creation time in milliseconds, so ids sort from oldest to newest.
     * The random part comes from UUID, which stays unique across Lambda environments
     * restored from the same SnapStart snapshot. A seeded generator would not.
     */
    static String newAlertId(Instant now) {
        return String.format("%013d-%s", now.toEpochMilli(), UUID.randomUUID().toString().substring(0, 8));
    }

    Alert create(String alertId, String fingerprint, Rule rule, Source source,
            NormalizedEvent first, long count, Instant lastSeenAt, Instant now) {
        Map<String, Object> variables = AlertVariables.of(alertId, rule, source, first, count, appUrl);
        Presentation presentation = rule.presentation();
        String title = presentation == null || presentation.title() == null
                ? rule.name()
                : Templates.render(presentation.title(), variables);
        String body = presentation == null || presentation.body() == null
                ? first.message()
                : Templates.render(presentation.body(), variables);

        return new Alert(
                alertId, rule.orgId(), rule.ruleId(), rule.name(), rule.sourceId(), fingerprint,
                rule.severity(), Alert.Status.OPEN, title, body, shownFields(presentation, first),
                count, first.eventTime(), lastSeenAt, first, rule.runbookId(),
                null, null, null, null, null,
                new Alert.Timing(first.receivedAt(), now));
    }

    private static Map<String, Object> shownFields(Presentation presentation, NormalizedEvent event) {
        Map<String, Object> shown = new LinkedHashMap<>();
        List<String> names = presentation == null || presentation.fields() == null ? List.of() : presentation.fields();
        for (String name : names) {
            Object value = EventFields.value(event, name);
            if (value != null) {
                shown.put(name, value);
            }
        }
        return shown;
    }
}
