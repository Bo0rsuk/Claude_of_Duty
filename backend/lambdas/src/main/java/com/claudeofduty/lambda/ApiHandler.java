package com.claudeofduty.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.claudeofduty.core.api.ApiException;
import com.claudeofduty.core.api.Caller;
import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.Member;
import com.claudeofduty.core.model.RecipientGroup;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Runbook;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.port.Membership;
import com.claudeofduty.lambda.ai.GeminiTextModel;
import com.claudeofduty.lambda.channels.TwilioVoiceChannel;
import com.claudeofduty.lambda.api.ApiRoutes;
import com.claudeofduty.lambda.api.Request;
import com.claudeofduty.lambda.api.Response;
import com.claudeofduty.lambda.api.Router;
import com.claudeofduty.lambda.api.Services;
import com.claudeofduty.aws.Env;
import com.claudeofduty.aws.SignedHttp;
import com.claudeofduty.aws.SqsNotificationQueue;
import com.claudeofduty.aws.SsmSecret;
import com.claudeofduty.aws.AppSyncAlertPublisher;
import com.claudeofduty.aws.AppSyncEventsClient;
import com.claudeofduty.aws.AwsClients;
import com.claudeofduty.aws.CognitoUserDirectory;
import com.claudeofduty.aws.DynamoAlertStore;
import com.claudeofduty.aws.DynamoMembership;
import com.claudeofduty.aws.DynamoPushSubscriptions;
import com.claudeofduty.aws.DynamoRecentEvents;
import com.claudeofduty.aws.DynamoRepository;
import com.claudeofduty.aws.DynamoThrottle;
import com.claudeofduty.aws.Tables;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Serves the REST API. This class authenticates the caller, checks the role
 * the route requires and turns results and failures into HTTP responses.
 * The routes themselves are listed in {@link ApiRoutes}.
 */
public class ApiHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final Router router;
    private final Membership membership;

    public ApiHandler() {
        this(defaultServices());
        // Lookups that find nothing, plus one pass through routing and response building.
        Priming.run(
                () -> membership.findMember("priming"),
                () -> membership.members("priming"),
                () -> router.match("GET", "/alerts"),
                () -> HttpResponses.error(404, "NOT_FOUND", "priming"));
    }

    ApiHandler(Services services) {
        this.router = ApiRoutes.create(services);
        this.membership = services.membership();
    }

    /** Path of the call script endpoint. Its API Gateway routes have no authorizer. */
    private static final String VOICE_SCRIPT_PATH = "/voice/twiml";

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        if (VOICE_SCRIPT_PATH.equals(event.getRawPath())) {
            return voiceScript(event);
        }
        try {
            Response response = handle(event);
            return response.body() == null
                    ? APIGatewayV2HTTPResponse.builder().withStatusCode(response.status()).build()
                    : HttpResponses.json(response.status(), response.body());
        } catch (ApiException e) {
            return HttpResponses.error(e.status(), e.code(), e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("Unhandled error for " + event.getRawPath() + ": " + e);
            e.printStackTrace();
            return HttpResponses.error(500, "INTERNAL", "Unexpected error");
        }
    }

    /**
     * What a phone call says, as TwiML. The phone provider fetches it when the recipient answers,
     * so it carries no token: the route has no authorizer, and the answer only echoes the text it was given.
     */
    private static APIGatewayV2HTTPResponse voiceScript(APIGatewayV2HTTPEvent event) {
        Map<String, String> query = event.getQueryStringParameters() == null ? Map.of() : event.getQueryStringParameters();
        String recording = Env.get("VOICE_RECORDING_URL", "");
        if (recording.startsWith("/")) {
            // A path on the web application, where the recording is published with the site.
            recording = Env.get("APP_URL", "") + recording;
        }
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(200)
                .withHeaders(Map.of("content-type", "text/xml; charset=utf-8"))
                .withBody(TwilioVoiceChannel.script(query.get("text"), recording))
                .build();
    }

    private Response handle(APIGatewayV2HTTPEvent event) {
        String method = event.getRequestContext().getHttp().getMethod();
        if ("OPTIONS".equals(method)) {
            // A browser's CORS preflight. It carries no token and only needs a success status:
            // API Gateway adds the Access-Control headers from its CORS settings.
            return Response.noContent();
        }
        Router.Match match = router.match(method, event.getRawPath())
                .orElseThrow(() -> ApiException.notFound("No such endpoint: " + method + " " + event.getRawPath()));

        Caller caller = caller(event);
        Member.Role required = match.route().minimumRole();
        if (required != null) {
            caller.orgId();
            if (!caller.has(required)) {
                throw ApiException.forbidden("This action needs the " + Json.convert(required, String.class) + " role");
            }
        }
        Map<String, String> query = event.getQueryStringParameters() == null ? Map.of() : event.getQueryStringParameters();
        return match.route().handler().handle(new Request(caller, match.pathParameters(), query, body(event)));
    }

    /** The JWT was already verified by API Gateway; this only reads its claims. */
    private Caller caller(APIGatewayV2HTTPEvent event) {
        var authorizer = event.getRequestContext().getAuthorizer();
        Map<String, String> claims = authorizer == null || authorizer.getJwt() == null
                ? Map.of()
                : authorizer.getJwt().getClaims();
        String userId = claims.get("sub");
        if (userId == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Missing or invalid token");
        }
        return new Caller(userId, claims.get("email"), membership.findMember(userId).orElse(null),
                claims.get(CognitoUserDirectory.ORGANIZATION_ATTRIBUTE));
    }

    private static JsonNode body(APIGatewayV2HTTPEvent event) {
        String body = event.getBody();
        if (body == null || body.isBlank()) {
            return null;
        }
        if (event.getIsBase64Encoded()) {
            body = new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
        }
        try {
            return Json.tree(body);
        } catch (UncheckedIOException e) {
            throw ApiException.invalid("The request body is not valid JSON");
        }
    }

    private static Services defaultServices() {
        DynamoDbClient dynamoDb = AwsClients.dynamoDb();
        Clock clock = Clock.systemUTC();
        SignedHttp signedHttp = AwsClients.signedHttp();
        AppSyncEventsClient events =
                new AppSyncEventsClient(signedHttp, URI.create(Env.required("EVENTS_HTTP_ENDPOINT")));
        // Secrets are fetched on first use, not when the function starts.
        SsmSecret geminiKey = new SsmSecret(
                signedHttp, AwsClients.region(), Env.get("GEMINI_API_KEY_PARAMETER", "/cod/gemini-api-key"));
        SsmSecret pushPublicKey = new SsmSecret(
                signedHttp, AwsClients.region(), Env.get("VAPID_PUBLIC_KEY_PARAMETER", "/cod/vapid-public-key"));
        return Services.create(new Services.Ports(
                new DynamoRepository<>(dynamoDb, Tables.SOURCES, "sourceId", Source.class),
                new DynamoRepository<>(dynamoDb, Tables.RULES, "ruleId", Rule.class),
                new DynamoRepository<>(dynamoDb, Tables.GROUPS, "groupId", RecipientGroup.class),
                new DynamoRepository<>(dynamoDb, Tables.RUNBOOKS, "runbookId", Runbook.class),
                new DynamoMembership(dynamoDb),
                new CognitoUserDirectory(signedHttp, AwsClients.region(), Env.required("USER_POOL_ID")),
                new DynamoAlertStore(dynamoDb),
                new AppSyncAlertPublisher(events, clock),
                new DynamoRecentEvents(dynamoDb, clock),
                new GeminiTextModel(Env.get("GEMINI_MODEL", "gemini-flash-lite-latest"), geminiKey, geminiKey::forget),
                new DynamoPushSubscriptions(dynamoDb),
                pushPublicKey,
                new DynamoThrottle(dynamoDb),
                new SqsNotificationQueue(AwsClients.sqs(), Env.required("NOTIFICATION_QUEUE_URLS")),
                Env.get("APP_URL", "")), clock);
    }
}
