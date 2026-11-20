package com.claudeofduty.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import java.util.Map;

/**
 * Serves the REST API from Docs/contracts/api.md. Only the health check exists
 * so far; the endpoints are added with tasks B5, B6 and B13-B16.
 */
public class ApiHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent request, Context context) {
        if ("GET /health".equals(request.getRouteKey()) || "/health".equals(request.getRawPath())) {
            return HttpResponses.json(200, Map.of("status", "ok"));
        }
        return HttpResponses.error(404, "NOT_FOUND", "No such endpoint: " + request.getRawPath());
    }
}
