package com.claudeofduty.aws;

import com.claudeofduty.core.json.Json;
import java.net.URI;
import java.util.Map;
import java.util.function.Supplier;
import software.amazon.awssdk.regions.Region;

/**
 * A secret kept in SSM Parameter Store as a SecureString. It is read on first
 * use and then remembered, so the secret never sits in the code, the
 * repository, the Terraform state or the function's configuration. A rotated
 * secret is picked up without a deployment once the old one is forgotten.
 */
public final class SsmSecret implements Supplier<String> {

    private static final Map<String, String> HEADERS = Map.of(
            "content-type", "application/x-amz-json-1.1",
            "x-amz-target", "AmazonSSM.GetParameter");

    private final SignedHttp http;
    private final URI endpoint;
    private final String parameterName;
    private volatile String value;

    public SsmSecret(SignedHttp http, Region region, String parameterName) {
        this.http = http;
        this.endpoint = URI.create("https://ssm." + region.id() + ".amazonaws.com/");
        this.parameterName = parameterName;
    }

    /** Drops the remembered value, so the next {@link #get()} reads the parameter again. Call it when the secret is rejected. */
    public void forget() {
        value = null;
    }

    /** @throws IllegalStateException when the parameter is missing or may not be read */
    @Override
    public String get() {
        String current = value;
        if (current == null) {
            SignedHttp.Response response = http.post(endpoint, "ssm", HEADERS,
                    Json.write(Map.of("Name", parameterName, "WithDecryption", true)));
            if (!response.successful()) {
                throw new IllegalStateException("Could not read parameter " + parameterName + ": HTTP "
                        + response.status() + " " + Json.tree(response.body()).path("__type").asText(""));
            }
            current = Json.tree(response.body()).path("Parameter").path("Value").asText();
            value = current;
        }
        return current;
    }
}
