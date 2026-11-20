package com.claudeofduty.core.model;

import java.time.Instant;

public record Organization(String orgId, String name, String inviteCode, Instant createdAt) {
}
