package com.claudeofduty.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.claudeofduty.core.ingest.WebhookIntake;
import com.claudeofduty.core.model.RawRecord;
import com.claudeofduty.core.port.RawRecordSink;
import com.claudeofduty.core.port.SourceLookup;
import com.claudeofduty.aws.Env;
import com.claudeofduty.aws.AwsClients;
import com.claudeofduty.aws.DynamoSourceLookup;
import com.claudeofduty.aws.KinesisRawRecordSink;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * POST /ingest/{orgId}/{sourceId} - accepts one payload from a webhook source,
 * stamps the time of arrival and puts it on the stream untouched.
 */
public class IngestHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final String TOKEN_HEADER = "x-ingest-token";
    private static final String TOKEN_PARAMETER = "token";
    private static final String PRIMING = "priming";

    private final SourceLookup sources;
    private final WebhookIntake intake;
    private final RawRecordSink sink;
    private final Clock clock;

    public IngestHandler() {
        this(new DynamoSourceLookup(AwsClients.dynamoDb()),
                new KinesisRawRecordSink(AwsClients.kinesis(), Env.required("STREAM_NAME")),
                Clock.systemUTC());
        // The record belongs to no source, so the processor skips it.
        Priming.run(
                () -> sources.find(PRIMING, PRIMING),
                () -> sink.publish(new RawRecord(PRIMING + "-" + UUID.randomUUID(), PRIMING, PRIMING, clock.instant(), "{}")),
                () -> HttpResponses.json(202, Map.of("recordId", PRIMING)));
    }

    IngestHandler(SourceLookup sources, RawRecordSink sink, Clock clock) {
        this.sources = sources;
        this.intake = new WebhookIntake(sources);
        this.sink = sink;
        this.clock = clock;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent request, Context context) {
        Instant receivedAt = clock.instant();

        Map<String, String> path = orEmpty(request.getPathParameters());
        String orgId = path.get("orgId");
        String sourceId = path.get("sourceId");
        if (orgId == null || sourceId == null || !intake.accepts(orgId, sourceId, token(request))) {
            return HttpResponses.error(404, "NOT_FOUND", "Unknown source or invalid token");
        }
        String payload = body(request);
        if (payload == null || payload.isBlank()) {
            return HttpResponses.error(400, "VALIDATION_FAILED", "Request body is empty");
        }

        RawRecord record = new RawRecord(UUID.randomUUID().toString(), orgId, sourceId, receivedAt, payload);
        sink.publish(record);
        return HttpResponses.json(202, Map.of("recordId", record.recordId()));
    }

    private static String token(APIGatewayV2HTTPEvent request) {
        String header = orEmpty(request.getHeaders()).get(TOKEN_HEADER);
        return header != null ? header : orEmpty(request.getQueryStringParameters()).get(TOKEN_PARAMETER);
    }

    private static String body(APIGatewayV2HTTPEvent request) {
        if (request.getBody() == null || !request.getIsBase64Encoded()) {
            return request.getBody();
        }
        return new String(Base64.getDecoder().decode(request.getBody()), StandardCharsets.UTF_8);
    }

    private static Map<String, String> orEmpty(Map<String, String> map) {
        return map == null ? Map.of() : map;
    }
}
