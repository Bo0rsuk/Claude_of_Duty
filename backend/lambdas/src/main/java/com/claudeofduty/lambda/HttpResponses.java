package com.claudeofduty.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.claudeofduty.core.json.Json;
import java.util.Map;

/** Builds API responses in the shapes defined in Docs/contracts/api.md. */
public final class HttpResponses {

    private static final Map<String, String> JSON_HEADERS = Map.of("content-type", "application/json");

    private HttpResponses() {
    }

    public static APIGatewayV2HTTPResponse json(int status, Object body) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(status)
                .withHeaders(JSON_HEADERS)
                .withBody(Json.write(body))
                .build();
    }

    public static APIGatewayV2HTTPResponse error(int status, String code, String message) {
        return json(status, Map.of("error", Map.of("code", code, "message", message)));
    }
}
