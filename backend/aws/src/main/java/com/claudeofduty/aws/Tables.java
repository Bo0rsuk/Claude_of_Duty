package com.claudeofduty.aws;


/** DynamoDB table names from Docs/contracts/data-model.md. */
public final class Tables {

    public static final String ORGS = name("orgs");
    public static final String MEMBERS = name("members");
    public static final String SOURCES = name("sources");
    public static final String GROUPS = name("groups");
    public static final String RUNBOOKS = name("runbooks");
    public static final String RULES = name("rules");
    public static final String ALERTS = name("alerts");
    public static final String ALERT_TIMELINE = name("alert-timeline");
    public static final String DEDUP = name("dedup");
    public static final String COUNTERS = name("counters");
    public static final String RECENT_EVENTS = name("recent-events");
    public static final String PUSH_SUBSCRIPTIONS = name("push-subscriptions");

    private Tables() {
    }

    public static String name(String entity) {
        return Env.get("TABLE_PREFIX", "cod-") + entity;
    }
}
