package com.claudeofduty.core.alerting;

import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.rules.EventFields;
import java.util.LinkedHashMap;
import java.util.Map;

/** The values available to templates, as listed in Docs/contracts/rules.md. */
public final class AlertVariables {

    private AlertVariables() {
    }

    public static Map<String, Object> of(
            String alertId, Rule rule, Source source, NormalizedEvent event, long count, String appUrl) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (event != null) {
            if (event.fields() != null) {
                variables.putAll(event.fields());
            }
            variables.put(EventFields.LEVEL, EventFields.value(event, EventFields.LEVEL));
            variables.put(EventFields.MESSAGE, event.message());
        }
        variables.put("severity", rule.severity());
        variables.put("ruleName", rule.name());
        variables.put("sourceName", source == null ? null : source.name());
        variables.put("count", count);
        variables.put("alertUrl", appUrl + "/alerts/" + alertId);
        return variables;
    }
}
