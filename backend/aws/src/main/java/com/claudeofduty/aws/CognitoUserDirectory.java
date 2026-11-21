package com.claudeofduty.aws;

import com.claudeofduty.core.api.ApiException;
import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.port.UserDirectory;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.regions.Region;

/**
 * Writes the user's organization to the Cognito attribute "custom:orgId". It
 * then appears in the user's ID token, which the real-time channels check.
 * Users cannot change this attribute themselves; only this code can.
 * Also creates the accounts of people an admin invites.
 */
public final class CognitoUserDirectory implements UserDirectory {

    public static final String ORGANIZATION_ATTRIBUTE = "custom:orgId";
    /** Written for a user without an organization. No channel exists for it, so it authorizes nothing. */
    private static final String NO_ORGANIZATION = "none";

    private static final Map<String, String> HEADERS = headers("AdminUpdateUserAttributes");
    private static final Map<String, String> CREATE_HEADERS = headers("AdminCreateUser");

    private static Map<String, String> headers(String action) {
        return Map.of(
                "content-type", "application/x-amz-json-1.1",
                "x-amz-target", "AWSCognitoIdentityProviderService." + action);
    }

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

    /** Cognito sends the invitation email with the temporary password itself. */
    @Override
    public String createUser(String email, String orgId) {
        SignedHttp.Response response = http.post(endpoint, "cognito-idp", CREATE_HEADERS, Json.write(Map.of(
                "UserPoolId", userPoolId,
                "Username", email,
                "DesiredDeliveryMediums", List.of("EMAIL"),
                "UserAttributes", List.of(
                        Map.of("Name", "email", "Value", email),
                        // The admin vouches for the address, and the temporary password proves it on first sign-in.
                        Map.of("Name", "email_verified", "Value", "true"),
                        Map.of("Name", ORGANIZATION_ATTRIBUTE, "Value", orgId)))));
        JsonNode body = Json.tree(response.body());
        if (!response.successful()) {
            String type = body.path("__type").asText();
            if (type.endsWith("UsernameExistsException")) {
                throw ApiException.conflict(email + " already has an account. Share the invite code with them instead.");
            }
            if (type.endsWith("InvalidParameterException")) {
                throw ApiException.invalid(body.path("message").asText("The sign-in system refused this email"));
            }
            throw new IllegalStateException("Could not create the account: HTTP " + response.status() + " " + response.body());
        }
        // The user id everywhere else is the "sub" claim of the token.
        for (JsonNode attribute : body.path("User").path("Attributes")) {
            if ("sub".equals(attribute.path("Name").asText())) {
                return attribute.path("Value").asText();
            }
        }
        throw new IllegalStateException("The sign-in system returned no id for the new account");
    }
}
