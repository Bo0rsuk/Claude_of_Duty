package com.claudeofduty.core.model;

import java.time.Instant;
import java.util.List;

/** A named set of recipients that rules can route to. */
public record RecipientGroup(
        String groupId,
        String orgId,
        String name,
        List<GroupMember> members,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * {@code type} "user" refers to an organization member by {@code userId};
     * "contact" is someone outside the organization with inline contact details.
     */
    public record GroupMember(String type, String userId, String name, String email, String phone) {
    }
}
