package com.claudeofduty.lambda.aws;

import com.claudeofduty.lambda.Env;

/** DynamoDB table names from Docs/contracts/data-model.md. */
public final class Tables {

    public static final String SOURCES = name("sources");
    public static final String RULES = name("rules");
    public static final String ALERTS = name("alerts");
    public static final String ALERT_TIMELINE = name("alert-timeline");
    public static final String DEDUP = name("dedup");

    private Tables() {
    }

    public static String name(String entity) {
        return Env.get("TABLE_PREFIX", "cod-") + entity;
    }
}
