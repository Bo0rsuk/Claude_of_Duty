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

    /**
     * Creates an account for a person invited by an admin. The sign-in system
     * emails them a temporary password, which they replace on first sign-in.
     *
     * @return the new user's id
     * @throws com.claudeofduty.core.api.ApiException with status 409 when the email already has an account
     */
    String createUser(String email, String orgId);
}
