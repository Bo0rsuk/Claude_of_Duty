package com.claudeofduty.aws;

import com.claudeofduty.core.json.Json;
import java.net.URI;
import java.util.List;
import java.util.Map;

/** Publishes to an AppSync Events channel over its HTTP endpoint. */
public final class AppSyncEventsClient {

    private static final String SIGNING_NAME = "appsync";
    private static final Map<String, String> HEADERS = Map.of("content-type", "application/json");

    private final SignedHttp http;
    private final URI endpoint;

    public AppSyncEventsClient(SignedHttp http, URI endpoint) {
        this.http = http;
        this.endpoint = endpoint;
    }

    /** @throws IllegalStateException when AppSync rejects the event */
    public void publish(String channel, Object event) {
        // The API takes each event as a JSON document serialized into a string.
        String body = Json.write(Map.of("channel", channel, "events", List.of(Json.write(event))));
        SignedHttp.Response response = http.post(endpoint, SIGNING_NAME, HEADERS, body);
        if (!response.successful()) {
            throw new IllegalStateException("AppSync Events answered " + response.status() + ": " + response.body());
        }
    }
}
