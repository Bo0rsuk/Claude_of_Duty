package com.claudeofduty.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** A user's membership in an organization, with the contact details used for notifications. */
public record Member(
        String orgId,
        String userId,
        Role role,
        String name,
        String email,
        String phone,
        Instant joinedAt) {

    /** Ordered from least to most privileged: each role can do everything the previous one can. */
    public enum Role {
        @JsonProperty("user") USER,
        @JsonProperty("team_lead") TEAM_LEAD,
        @JsonProperty("admin") ADMIN;

        public boolean atLeast(Role required) {
            return compareTo(required) >= 0;
        }
    }
}
