package com.claudeofduty.core.api;

import com.claudeofduty.core.model.Member;
import com.claudeofduty.core.model.TimelineEntry;

/**
 * The authenticated user behind a request.
 *
 * @param member     null until the user joins an organization
 * @param tokenOrgId the organization recorded in the user's token, which lags behind {@code member}
 *                   until the token is refreshed
 */
public record Caller(String userId, String email, Member member, String tokenOrgId) {

    public Caller(String userId, String email, Member member) {
        this(userId, email, member, member == null ? null : member.orgId());
    }

    /** @throws ApiException when the user has not joined an organization yet */
    public String orgId() {
        if (member == null) {
            throw ApiException.forbidden("Join or create an organization first");
        }
        return member.orgId();
    }

    public boolean has(Member.Role role) {
        return member != null && member.role().atLeast(role);
    }

    public String displayName() {
        return member != null && member.name() != null ? member.name() : email;
    }

    public TimelineEntry.Actor actor() {
        return TimelineEntry.Actor.user(userId, displayName());
    }
}
