package com.claudeofduty.core.port;

/** The sign-in system's record of a user, as far as this application writes to it. */
public interface UserDirectory {

    /**
     * Records which organization the user belongs to, so that it appears in
     * the user's next token. Real-time channels are authorized against it.
     *
     * @param orgId the organization, or null when the user no longer belongs to one
     */
    void setOrganization(String userId, String orgId);
}
