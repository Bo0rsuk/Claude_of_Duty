package com.claudeofduty.aws;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.port.UserDirectory;
import java.net.URI;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.regions.Region;

/**
 * Writes the user's organization to the Cognito attribute "custom:orgId". It
 * then appears in the user's ID token, which the real-time channels check.
 * Users cannot change this attribute themselves; only this code can.
 */
public final class CognitoUserDirectory implements UserDirectory {

    public static final String ORGANIZATION_ATTRIBUTE = "custom:orgId";
    /** Written for a user without an organization. No channel exists for it, so it authorizes nothing. */
    private static final String NO_ORGANIZATION = "none";

    private static final Map<String, String> HEADERS = Map.of(
            "content-type", "application/x-amz-json-1.1",
            "x-amz-target", "AWSCognitoIdentityProviderService.AdminUpdateUserAttributes");

    private final SignedHttp http;
    private final URI endpoint;
    private final String userPoolId;

    public CognitoUserDirectory(SignedHttp http, Region region, String userPoolId) {
        this.http = http;
        this.endpoint = URI.create("https://cognito-idp." + region.id() + ".amazonaws.com/");
        this.userPoolId = userPoolId;
    }

    /**
     * A failure is logged, not thrown: membership is already saved, and reading
     * the profile repairs a missing attribute the next time the user opens the app.
     */
    @Override
    public void setOrganization(String userId, String orgId) {
        try {
            SignedHttp.Response response = http.post(endpoint, "cognito-idp", HEADERS, Json.write(Map.of(
                    "UserPoolId", userPoolId,
                    "Username", userId,
                    "UserAttributes", List.of(Map.of("Name", ORGANIZATION_ATTRIBUTE, "Value", orgId == null ? NO_ORGANIZATION : orgId)))));
            if (!response.successful()) {
                System.err.println("Could not record the organization of user " + userId + ": HTTP "
                        + response.status() + " " + response.body());
            }
        } catch (RuntimeException e) {
            System.err.println("Could not record the organization of user " + userId + ": " + e);
        }
    }
}
