package com.claudeofduty.core.model;

import java.time.Instant;
import java.util.List;

/** Instructions attached to alerts. {@code format} decides which content field is used. */
public record Runbook(
        String runbookId,
        String orgId,
        String title,
        String format,
        String content,
        String url,
        List<String> items,
        Instant createdAt,
        Instant updatedAt) {

    public static final String MARKDOWN = "markdown";
    public static final String TEXT = "text";
    public static final String LINK = "link";
    public static final String CHECKLIST = "checklist";
}
