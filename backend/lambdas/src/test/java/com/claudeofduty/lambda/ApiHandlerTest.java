package com.claudeofduty.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.claudeofduty.core.api.ApiException;
import com.claudeofduty.core.port.UserDirectory;
import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.Level;
import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.NotificationJob;
import com.claudeofduty.core.model.PushSubscription;
import com.claudeofduty.core.model.RecipientGroup;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Runbook;
import com.claudeofduty.core.model.Severity;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.port.PushSubscriptions;
import com.claudeofduty.core.port.RecentEvents;
import com.claudeofduty.core.push.Vapid;
import com.claudeofduty.lambda.api.Services;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Drives the API through the real handler and routes, with in-memory storage. */
class ApiHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-20T21:00:00.000Z");
    private static final String WEBHOOK_SOURCE = "{\"name\": \"Monitoring\", \"connector\": {\"type\": \"webhook\"}}";

    private final InMemory.Store<Source> sources = new InMemory.Store<>(Source::orgId, Source::sourceId);
    private final InMemory.Store<Rule> rules = new InMemory.Store<>(Rule::orgId, Rule::ruleId);
    private final InMemory.Store<RecipientGroup> groups = new InMemory.Store<>(RecipientGroup::orgId, RecipientGroup::groupId);
    private final InMemory.Store<Runbook> runbooks = new InMemory.Store<>(Runbook::orgId, Runbook::runbookId);
    private final InMemory.Members members = new InMemory.Members();
    private final InMemory.Alerts alerts = new InMemory.Alerts();
    private final List<String> published = new ArrayList<>();
    private final List<NormalizedEvent> recentEvents = new ArrayList<>();

    private final List<NotificationJob> queued = new ArrayList<>();
    private final List<PushSubscription> pushSubscriptions = new ArrayList<>();
    /** What the sign-in system has on record: user -> organization. A token carries it only after a refresh. */
    private final Map<String, String> organizationOnRecord = new HashMap<>();
    private final Map<String, String> organizationInToken = new HashMap<>();
    /** Addresses changed in the sign-in system: user -> new email. */
    private final Map<String, String> signInEmails = new HashMap<>();

    private final ApiHandler api = new ApiHandler(Services.create(new Services.Ports(
            sources, rules, groups, runbooks, members,
            new UserDirectory() {
                @Override
                public void setOrganization(String userId, String orgId) {
                    organizationOnRecord.put(userId, orgId == null ? "" : orgId);
                }

                @Override
                public String createUser(String email, String orgId) {
                    String userId = email.substring(0, email.indexOf('@'));
                    if (organizationOnRecord.containsKey(userId)) {
                        throw ApiException.conflict(email + " already has an account");
                    }
                    organizationOnRecord.put(userId, orgId);
                    return userId;
                }

                @Override
                public void changeEmail(String userId, String email) {
                    if (email.startsWith("taken@")) {
                        throw ApiException.conflict(email + " already has an account");
                    }
                    signInEmails.put(userId, email);
                }
            },
            alerts,
            (type, alert) -> published.add(type + " " + alert.status()),
            new RecentEvents() {
                @Override
                public void saveAll(List<NormalizedEvent> events) {
                }

                @Override
                public List<NormalizedEvent> latest(String orgId, String sourceId, int limit) {
                    return recentEvents.stream().limit(limit).toList();
                }
            },
            (instructions, input) -> "{\"mode\": \"all\", \"conditions\": [{\"field\": \"level\", \"op\": \"eq\","
                    + " \"value\": \"ERROR\"}], \"severity\": \"CRITICAL\", \"explanation\": \"Errors only.\"}",
            new PushSubscriptions() {
                @Override
                public List<PushSubscription> of(String userId) {
                    return pushSubscriptions.stream().filter(s -> s.userId().equals(userId)).toList();
                }

                @Override
                public void save(PushSubscription subscription) {
                    delete(subscription.userId(), subscription.subscriptionId());
                    pushSubscriptions.add(subscription);
                }

                @Override
                public void delete(String userId, String subscriptionId) {
                    pushSubscriptions.removeIf(s -> s.userId().equals(userId) && s.subscriptionId().equals(subscriptionId));
                }
            },
            () -> "server-public-key",
            (counterKey, max, windowEnd) -> false,
            (job, delaySeconds) -> queued.add(job),
            "https://app.example.pl"), Clock.fixed(NOW, ZoneOffset.UTC)));

    @Test
    void newUserCreatesAnOrganizationAndBecomesItsAdmin() {
        assertTrue(call("anna", "GET", "/me", null).body.get("org").isNull());

        Result created = call("anna", "POST", "/orgs", "{\"name\": \"Cloud team\"}");
        assertEquals(201, created.status);
        assertTrue(created.body.get("inviteCode").asText().matches("[A-Z2-9]{4}-[A-Z2-9]{4}"));

        JsonNode me = call("anna", "GET", "/me", null).body;
        assertEquals("Cloud team", me.get("org").get("name").asText());
        assertEquals("admin", me.get("org").get("role").asText());
        assertEquals("anna@example.pl", me.get("email").asText());
        assertEquals(409, call("anna", "POST", "/orgs", "{\"name\": \"Another\"}").status);
    }

    @Test
    void secondUserJoinsByInviteCodeAndIsLimitedUntilPromoted() {
        String inviteCode = createOrganization("anna");

        assertEquals(404, call("bob", "POST", "/orgs/join", "{\"inviteCode\": \"NOPE-NOPE\"}").status);
        Result joined = call("bob", "POST", "/orgs/join", "{\"inviteCode\": \"" + inviteCode.toLowerCase() + "\"}");
        assertEquals(200, joined.status);
        assertFalse(joined.body.hasNonNull("inviteCode"));

        assertEquals("user", call("bob", "GET", "/me", null).body.get("org").get("role").asText());
        assertFalse(call("bob", "GET", "/org", null).body.hasNonNull("inviteCode"));
        assertEquals(2, call("bob", "GET", "/members", null).body.get("items").size());
        assertEquals(403, call("bob", "POST", "/sources", WEBHOOK_SOURCE).status);
        assertEquals(403, call("bob", "PATCH", "/members/anna", "{\"role\": \"user\"}").status);

        assertEquals(200, call("anna", "PATCH", "/members/bob", "{\"role\": \"team_lead\"}").status);
        assertEquals(201, call("bob", "POST", "/sources", WEBHOOK_SOURCE).status);
        assertEquals(403, call("bob", "POST", "/org/invite-code", null).status);
    }

    @Test
    void adminAddsAPersonByEmailWhoIsThenAMemberWithTheChosenRole() {
        createOrganization("anna");
        String orgId = members.findMember("anna").orElseThrow().orgId();

        Result added = call("anna", "POST", "/members",
                "{\"email\": \" Bob@Example.pl \", \"name\": \"Bob\", \"role\": \"team_lead\"}");
        assertEquals(201, added.status);
        assertEquals("bob", added.body.get("userId").asText());
        assertEquals("bob@example.pl", added.body.get("email").asText());
        // The account is created already in the organization, so Bob's first token carries it.
        assertEquals(orgId, organizationOnRecord.get("bob"));
        assertEquals("team_lead", call("bob", "GET", "/me", null).body.get("org").get("role").asText());

        assertEquals("user", call("anna", "POST", "/members", "{\"email\": \"carl@example.pl\"}").body.get("role").asText());
        assertEquals(409, call("anna", "POST", "/members", "{\"email\": \"bob@example.pl\"}").status);
        assertEquals(400, call("anna", "POST", "/members", "{\"email\": \"not an address\"}").status);
        assertEquals(403, call("carl", "POST", "/members", "{\"email\": \"dan@example.pl\"}").status);
    }

    @Test
    void emailChangesInTheProfileAndInTheSignInSystemTogether() {
        String inviteCode = createOrganization("anna");
        call("bob", "POST", "/orgs/join", "{\"inviteCode\": \"" + inviteCode + "\"}");

        Result own = call("anna", "PATCH", "/me", "{\"email\": \" Anna.New@Example.pl \"}");
        assertEquals(200, own.status);
        assertEquals("anna.new@example.pl", own.body.get("email").asText());
        assertEquals("anna.new@example.pl", signInEmails.get("anna"));

        Result other = call("anna", "PATCH", "/members/bob", "{\"email\": \"robert@example.pl\"}");
        assertEquals("robert@example.pl", other.body.get("email").asText());
        assertEquals("robert@example.pl", signInEmails.get("bob"));

        // Refused changes leave both places as they were.
        assertEquals(409, call("anna", "PATCH", "/members/bob", "{\"email\": \"anna.new@example.pl\"}").status);
        assertEquals(409, call("anna", "PATCH", "/members/bob", "{\"email\": \"taken@example.pl\"}").status);
        assertEquals(400, call("anna", "PATCH", "/me", "{\"email\": \"not an address\"}").status);
        assertEquals(403, call("bob", "PATCH", "/members/anna", "{\"email\": \"x@example.pl\"}").status);
        assertEquals("robert@example.pl", call("anna", "GET", "/members", null).body.get("items").findValuesAsText("email")
                .stream().filter(email -> email.startsWith("robert")).findFirst().orElse(null));
        assertEquals("robert@example.pl", signInEmails.get("bob"));
    }

    @Test
    void phoneProviderGetsTheCallScriptWithoutSigningIn() {
        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withRawPath("/voice/twiml")
                .withQueryStringParameters(Map.of("text", "payment-api is failing <now>"))
                .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                        .withHttp(APIGatewayV2HTTPEvent.RequestContext.Http.builder().withMethod("POST").build())
                        .build())
                .build();

        APIGatewayV2HTTPResponse response = api.handleRequest(event, null);

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getHeaders().get("content-type").startsWith("text/xml"));
        assertTrue(response.getBody().contains("<Say language=\"en-US\">payment-api is failing &lt;now&gt;</Say>"),
                response.getBody());
    }

    @Test
    void organizationAlwaysKeepsAnAdmin() {
        createOrganization("anna");

        assertEquals(409, call("anna", "PATCH", "/members/anna", "{\"role\": \"user\"}").status);
        assertEquals(409, call("anna", "DELETE", "/members/anna", null).status);
        assertEquals(400, call("anna", "PATCH", "/members/anna", "{\"role\": \"owner\"}").status);
    }

    @Test
    void profileHoldsTheContactDetailsUsedForNotifications() {
        createOrganization("anna");

        JsonNode profile = call("anna", "PATCH", "/me", "{\"name\": \"Anna Nowak\", \"phone\": \"+48600100200\"}").body;

        assertEquals("Anna Nowak", profile.get("name").asText());
        assertEquals("+48600100200", call("anna", "GET", "/members", null).body.get("items").get(0).get("phone").asText());
    }

    @Test
    void sourcesAndRulesAreManagedAtRuntime() {
        createOrganization("anna");

        JsonNode source = call("anna", "POST", "/sources", WEBHOOK_SOURCE).body;
        String sourceId = source.get("sourceId").asText();
        assertTrue(source.get("enabled").asBoolean());
        assertEquals(32, source.get("ingestToken").asText().length());
        assertEquals("2026-11-20T21:00:00.000Z", source.get("createdAt").asText());

        String rule = "{\"name\": \"Errors\", \"sourceId\": \"" + sourceId + "\", \"severity\": \"CRITICAL\","
                + "\"match\": {\"mode\": \"all\", \"conditions\": [{\"field\": \"level\", \"op\": \"eq\", \"value\": \"ERROR\"}]}}";
        Result created = call("anna", "POST", "/rules", rule);
        assertEquals(201, created.status);
        String ruleId = created.body.get("ruleId").asText();
        assertEquals("anna", created.body.get("updatedBy").asText());

        Result replaced = call("anna", "PUT", "/rules/" + ruleId, rule.replace("Errors", "All errors"));
        assertEquals("All errors", replaced.body.get("name").asText());
        assertEquals(ruleId, replaced.body.get("ruleId").asText());
        assertEquals("All errors", call("anna", "GET", "/rules/" + ruleId, null).body.get("name").asText());
        assertEquals(1, call("anna", "GET", "/rules?sourceId=" + sourceId, null).body.get("items").size());
        assertEquals(0, call("anna", "GET", "/rules?sourceId=other", null).body.get("items").size());

        // Replacing a source keeps the token that senders already use.
        Result renamed = call("anna", "PUT", "/sources/" + sourceId, WEBHOOK_SOURCE.replace("Monitoring", "Prometheus"));
        assertEquals(source.get("ingestToken").asText(), renamed.body.get("ingestToken").asText());

        assertEquals(204, call("anna", "DELETE", "/sources/" + sourceId, null).status);
        assertEquals(404, call("anna", "GET", "/rules/" + ruleId, null).status);
        assertEquals(404, call("anna", "GET", "/sources/" + sourceId, null).status);
    }

    @Test
    void invalidConfigurationIsRejectedWithAReason() {
        createOrganization("anna");
        String sourceId = call("anna", "POST", "/sources", WEBHOOK_SOURCE).body.get("sourceId").asText();
        String rule = "{\"name\": \"Errors\", \"sourceId\": \"%s\", \"severity\": \"%s\","
                + "\"match\": {\"mode\": \"all\", \"conditions\": [{\"field\": \"level\", \"op\": \"%s\", \"value\": \"%s\"}]}}";

        assertInvalid("Unknown operator", call("anna", "POST", "/rules", rule.formatted(sourceId, "CRITICAL", "soundsLike", "x")));
        assertInvalid("regular expression", call("anna", "POST", "/rules", rule.formatted(sourceId, "CRITICAL", "regex", "(unclosed")));
        assertInvalid("existing source", call("anna", "POST", "/rules", rule.formatted("missing", "CRITICAL", "eq", "x")));
        String escalating = rule.formatted(sourceId, "CRITICAL", "eq", "x").replaceFirst("\\}$", ", \"escalation\": {\"stepSeconds\": %d}}");
        assertInvalid("escalation.stepSeconds", call("anna", "POST", "/rules", escalating.formatted(2)));
        assertInvalid("escalation.stepSeconds", call("anna", "POST", "/rules", escalating.formatted(3600)));
        Result saved = call("anna", "POST", "/rules", escalating.formatted(30));
        assertEquals(201, saved.status);
        assertEquals(30, saved.body.get("escalation").get("stepSeconds").asInt());
        assertInvalid("shape of a Rule", call("anna", "POST", "/rules", rule.formatted(sourceId, "URGENT", "eq", "x")));
        assertInvalid("connector.type", call("anna", "POST", "/sources", "{\"name\": \"X\", \"connector\": {\"type\": \"ftp\"}}"));
        assertInvalid("parser", call("anna", "POST", "/sources",
                "{\"name\": \"X\", \"connector\": {\"type\": \"webhook\"}, \"parser\": {\"format\": \"xml\"}}"));
        assertInvalid("format", call("anna", "POST", "/runbooks", "{\"title\": \"Restart\", \"format\": \"video\"}"));
        assertInvalid("valid JSON", call("anna", "POST", "/groups", "{not json"));
        assertEquals(201, call("anna", "POST", "/groups", "{\"name\": \"cloud_engs\", \"members\": []}").status);
        assertEquals(201, call("anna", "POST", "/runbooks",
                "{\"title\": \"Restart\", \"format\": \"checklist\", \"items\": [\"Drain\", \"Restart\"]}").status);
    }

    @Test
    void presetsAndPreviewSupportTheSourceWizard() {
        createOrganization("anna");

        JsonNode presets = call("anna", "GET", "/source-presets", null).body.get("items");
        assertEquals(5, presets.size());
        JsonNode applicationLog = presets.get(1);
        assertEquals("regex", applicationLog.get("parser").get("format").asText());

        String sample = "2026-11-20 20:59:22,373 WARN  [inventory-service] [inventory-service-1] [trace=caa27ec2] RETRY - Retrying\\n"
                + "2026-11-20 20:59:23,001 ERROR [order-service] [order-service-2] [trace=11aa22bb] DB_TIMEOUT - Timeout";
        Result preview = call("anna", "POST", "/sources/preview",
                "{\"parser\": " + applicationLog.get("parser") + ", \"sample\": \"" + sample + "\"}");

        assertEquals(200, preview.status);
        JsonNode events = preview.body.get("events");
        assertEquals(2, events.size());
        assertEquals("WARN", events.get(0).get("level").asText());
        assertEquals("order-service", events.get(1).get("fields").get("service").asText());
        assertEquals("2026-11-20T20:59:23.001Z", events.get(1).get("eventTime").asText());
    }

    @Test
    void ruleEditorCanPreviewARuleAndAskTheAssistant() {
        createOrganization("anna");
        String sourceId = call("anna", "POST", "/sources", WEBHOOK_SOURCE).body.get("sourceId").asText();
        for (Level level : List.of(Level.ERROR, Level.INFO, Level.INFO, Level.ERROR)) {
            recentEvents.add(new NormalizedEvent("evt-" + recentEvents.size(), "org", sourceId, NOW, NOW, level,
                    "message", Map.of("service", "payment-api"), "raw"));
        }

        JsonNode events = call("anna", "GET", "/sources/" + sourceId + "/events?limit=3", null).body.get("items");
        assertEquals(3, events.size());
        assertEquals("payment-api", events.get(0).get("fields").get("service").asText());

        Result preview = call("anna", "POST", "/rules/preview", "{\"sourceId\": \"" + sourceId + "\", \"match\": "
                + "{\"mode\": \"all\", \"conditions\": [{\"field\": \"level\", \"op\": \"eq\", \"value\": \"INFO\"}]}}");
        assertEquals(200, preview.status);
        assertEquals(2, preview.body.get("matched").asInt());
        assertEquals(4, preview.body.get("total").asInt());
        assertEquals(2, preview.body.get("samples").size());

        Result suggestion = call("anna", "POST", "/ai/rule-suggestion",
                "{\"sourceId\": \"" + sourceId + "\", \"goal\": \"Tylko błędy\"}");
        assertEquals(200, suggestion.status);
        assertEquals("all", suggestion.body.get("match").get("mode").asText());
        assertEquals("ERROR", suggestion.body.get("match").get("conditions").get(0).get("value").asText());
        assertEquals("CRITICAL", suggestion.body.get("severity").asText());
        assertEquals("Errors only.", suggestion.body.get("explanation").asText());
        assertEquals(2, suggestion.body.get("preview").get("matched").asInt());

        assertEquals(400, call("anna", "POST", "/ai/rule-suggestion", "{\"sourceId\": \"" + sourceId + "\"}").status);
        assertEquals(404, call("anna", "POST", "/rules/preview", "{\"sourceId\": \"missing\", \"match\": null}").status);
        assertEquals(400, call("anna", "POST", "/rules/preview",
                "{\"sourceId\": \"" + sourceId + "\", \"match\": {\"mode\": \"sometimes\"}}").status);
    }

    @Test
    void tokenMustBeRefreshedAfterJoiningBeforeRealTimeChannelsWork() {
        assertFalse(call("anna", "GET", "/me", null).body.get("tokenRefreshNeeded").asBoolean());

        createOrganization("anna");
        String orgId = members.findMember("anna").orElseThrow().orgId();
        assertEquals(orgId, organizationOnRecord.get("anna"));

        // The token Anna holds was issued before she had an organization.
        assertTrue(call("anna", "GET", "/me", null).body.get("tokenRefreshNeeded").asBoolean());
        organizationInToken.put("anna", orgId);
        assertFalse(call("anna", "GET", "/me", null).body.get("tokenRefreshNeeded").asBoolean());

        // A member from before this was recorded gets repaired just by opening the app.
        organizationOnRecord.clear();
        organizationInToken.clear();
        call("anna", "GET", "/me", null);
        assertEquals(orgId, organizationOnRecord.get("anna"));

        String inviteCode = call("anna", "GET", "/org", null).body.get("inviteCode").asText();
        call("bob", "POST", "/orgs/join", "{\"inviteCode\": \"" + inviteCode + "\"}");
        assertEquals(orgId, organizationOnRecord.get("bob"));
        call("anna", "DELETE", "/members/bob", null);
        assertEquals("", organizationOnRecord.get("bob"));
    }

    @Test
    void userRegistersDevicesForPushNotifications() {
        assertEquals("server-public-key", call("anna", "GET", "/push/public-key", null).body.get("publicKey").asText());
        String browserKey = Vapid.Keys.generate().publicKey();
        String subscription = "{\"endpoint\": \"https://fcm.googleapis.com/fcm/send/device-1\", \"expirationTime\": null,"
                + " \"keys\": {\"p256dh\": \"" + browserKey + "\", \"auth\": \"BTBZMqHH6r4Tts7J_aSIgg\"}}";

        Result registered = call("anna", "POST", "/me/push-subscriptions", subscription);
        assertEquals(201, registered.status);
        String id = registered.body.get("subscriptionId").asText();
        assertEquals(201, call("anna", "POST", "/me/push-subscriptions", subscription).status);
        assertEquals(1, call("anna", "GET", "/me/push-subscriptions", null).body.get("items").size());
        assertEquals(0, call("bob", "GET", "/me/push-subscriptions", null).body.get("items").size());
        assertEquals(400, call("anna", "POST", "/me/push-subscriptions", "{\"endpoint\": \"https://x.example/y\"}").status);

        assertEquals(204, call("anna", "DELETE", "/me/push-subscriptions/" + id, null).status);
        assertEquals(0, call("anna", "GET", "/me/push-subscriptions", null).body.get("items").size());
    }

    @Test
    void notificationsCanBeSentAgainOnRequest() {
        createOrganization("anna");
        String orgId = members.findMember("anna").orElseThrow().orgId();
        String sourceId = call("anna", "POST", "/sources", WEBHOOK_SOURCE).body.get("sourceId").asText();
        String ruleId = call("anna", "POST", "/rules", "{\"name\": \"Errors\", \"sourceId\": \"" + sourceId
                + "\", \"severity\": \"CRITICAL\", \"match\": {\"mode\": \"all\", \"conditions\": []},"
                + " \"throttle\": {\"perRule\": {\"max\": 1, \"windowSeconds\": 300}},"
                + " \"routing\": [{\"channel\": \"email\", \"target\": {\"type\": \"address\", \"value\": \"oncall@example.pl\"}},"
                + " {\"channel\": \"push\", \"target\": {\"type\": \"user\", \"id\": \"anna\"}}]}").body.get("ruleId").asText();
        Alert original = alert("1795208362373-aaaaaaaa", orgId, Severity.CRITICAL);
        alerts.alerts.put(original.alertId(), new Alert(original.alertId(), orgId, ruleId, "Errors", sourceId,
                original.fingerprint(), original.severity(), original.status(), original.title(), original.body(),
                original.fields(), original.count(), original.firstSeenAt(), original.lastSeenAt(), null, null,
                null, null, null, null, null, original.timing(), null));

        Result resent = call("anna", "POST", "/alerts/" + original.alertId() + "/resend", null);

        assertEquals(200, resent.status);
        // The test throttle refuses everything, and a resend still goes out: it ignores throttling.
        assertEquals(List.of("email oncall@example.pl", "push anna"),
                queued.stream().map(job -> job.channel() + " " + job.recipient().address()).toList());
        JsonNode timeline = call("anna", "GET", "/alerts/" + original.alertId() + "/timeline", null).body.get("items");
        assertEquals("NOTIFICATIONS_RESENT", timeline.get(0).get("type").asText());
        assertEquals(2, timeline.get(0).get("detail").get("notifications").asInt());
        assertEquals("anna", timeline.get(0).get("actor").get("id").asText());

        call("anna", "DELETE", "/rules/" + ruleId, null);
        assertEquals(409, call("anna", "POST", "/alerts/" + original.alertId() + "/resend", null).status);
    }

    @Test
    void alertsAreListedAndActedOnWithEveryActionInTheTimeline() {
        createOrganization("anna");
        String orgId = members.findMember("anna").orElseThrow().orgId();
        runbooks.save(new Runbook("rb-1", orgId, "Database timeouts", Runbook.TEXT, "Fail over", null, null, NOW, NOW));
        alerts.alerts.put("1795208362373-aaaaaaaa", alert("1795208362373-aaaaaaaa", orgId, Severity.CRITICAL));
        alerts.alerts.put("1795208362999-bbbbbbbb", alert("1795208362999-bbbbbbbb", orgId, Severity.WARNING));
        alerts.alerts.put("1795208363000-cccccccc", alert("1795208363000-cccccccc", "another-org", Severity.CRITICAL));

        JsonNode list = call("anna", "GET", "/alerts", null).body;
        assertEquals(List.of("1795208362999-bbbbbbbb", "1795208362373-aaaaaaaa"), ids(list));
        assertFalse(list.has("nextCursor"));
        assertFalse(list.get("items").get(0).hasNonNull("sampleEvent"));
        assertEquals(List.of("1795208362373-aaaaaaaa"), ids(call("anna", "GET", "/alerts?severity=CRITICAL", null).body));
        JsonNode firstPage = call("anna", "GET", "/alerts?limit=1", null).body;
        assertEquals("1795208362999-bbbbbbbb", firstPage.get("nextCursor").asText());
        assertEquals(List.of("1795208362373-aaaaaaaa"),
                ids(call("anna", "GET", "/alerts?limit=1&cursor=" + firstPage.get("nextCursor").asText(), null).body));
        assertEquals(400, call("anna", "GET", "/alerts?status=BROKEN", null).status);

        String path = "/alerts/1795208362373-aaaaaaaa";
        JsonNode details = call("anna", "GET", path, null).body;
        assertEquals("payment-api: DB_TIMEOUT", details.get("title").asText());
        assertEquals("Database timeouts", details.get("runbook").get("title").asText());
        assertEquals(404, call("anna", "GET", "/alerts/1795208363000-cccccccc", null).status);

        JsonNode acknowledged = call("anna", "POST", path + "/ack", null).body;
        assertEquals("ACKNOWLEDGED", acknowledged.get("status").asText());
        assertEquals("anna", acknowledged.get("acknowledgedBy").asText());
        assertEquals("2026-11-20T21:00:00.000Z", acknowledged.get("acknowledgedAt").asText());
        assertEquals(409, call("anna", "POST", path + "/ack", null).status);

        assertEquals(200, call("anna", "POST", path + "/notes", "{\"text\": \"Restarting the pool\"}").status);
        assertEquals(400, call("anna", "POST", path + "/notes", "{\"text\": \" \"}").status);
        assertEquals("anna", call("anna", "POST", path + "/assign", "{\"userId\": \"anna\"}").body.get("assigneeId").asText());
        assertEquals(400, call("anna", "POST", path + "/assign", "{\"userId\": \"stranger\"}").status);

        JsonNode resolved = call("anna", "POST", path + "/resolve", null).body;
        assertEquals("RESOLVED", resolved.get("status").asText());
        assertEquals(409, call("anna", "POST", path + "/resolve", null).status);
        assertEquals(List.of("fingerprint-1795208362373-aaaaaaaa"), alerts.released);

        JsonNode timeline = call("anna", "GET", path + "/timeline", null).body.get("items");
        List<String> types = new ArrayList<>();
        timeline.forEach(entry -> types.add(entry.get("type").asText()));
        assertEquals(List.of("ACKNOWLEDGED", "NOTE", "ASSIGNED", "RESOLVED"), types);
        assertEquals("user", timeline.get(0).get("actor").get("type").asText());
        assertEquals("anna", timeline.get(0).get("actor").get("id").asText());
        assertEquals("Restarting the pool", timeline.get(1).get("detail").get("text").asText());
        assertEquals(List.of("alert.updated ACKNOWLEDGED", "alert.updated ACKNOWLEDGED", "alert.updated ACKNOWLEDGED",
                "alert.updated RESOLVED"), published);
    }

    @Test
    void runbookStepsTickedOnAnAlertAreSharedByEveryoneLookingAtIt() {
        String code = createOrganization("anna");
        call("bob", "POST", "/orgs/join", "{\"inviteCode\": \"" + code + "\"}");
        String orgId = members.findMember("anna").orElseThrow().orgId();
        alerts.alerts.put("1795208362373-aaaaaaaa", alert("1795208362373-aaaaaaaa", orgId, Severity.CRITICAL));
        String path = "/alerts/1795208362373-aaaaaaaa";

        // A plain user works through the runbook too.
        JsonNode ticked = call("bob", "PUT", path + "/runbook-steps", "{\"checked\": [2, 0, 2]}").body;

        assertEquals("[0,2]", ticked.get("checkedSteps").toString());
        assertEquals("[0,2]", call("anna", "GET", path, null).body.get("checkedSteps").toString());
        assertEquals("[0,2]", call("anna", "GET", "/alerts", null).body.get("items").get(0).get("checkedSteps").toString());
        assertEquals(List.of("alert.updated OPEN"), published);
        assertEquals("[]", call("anna", "PUT", path + "/runbook-steps", "{\"checked\": []}").body.get("checkedSteps").toString());
        assertInvalid("step positions", call("anna", "PUT", path + "/runbook-steps", "{\"checked\": [-1]}"));
        assertInvalid("step positions", call("anna", "PUT", path + "/runbook-steps", "{\"checked\": [\"first\"]}"));
        assertInvalid("step positions", call("anna", "PUT", path + "/runbook-steps", "{}"));
        assertEquals(404, call("anna", "PUT", "/alerts/missing/runbook-steps", "{\"checked\": []}").status);
    }

    @Test
    void teamLeadDeletesAnAlertWithItsHistory() {
        String code = createOrganization("anna");
        call("bob", "POST", "/orgs/join", "{\"inviteCode\": \"" + code + "\"}");
        String orgId = members.findMember("anna").orElseThrow().orgId();
        alerts.alerts.put("1795208362373-aaaaaaaa", alert("1795208362373-aaaaaaaa", orgId, Severity.CRITICAL));
        alerts.alerts.put("1795208362999-bbbbbbbb", alert("1795208362999-bbbbbbbb", orgId, Severity.WARNING));
        alerts.alerts.put("1795208363000-cccccccc", alert("1795208363000-cccccccc", "another-org", Severity.CRITICAL));
        String path = "/alerts/1795208362373-aaaaaaaa";
        call("anna", "POST", path + "/notes", "{\"text\": \"False alarm\"}");
        published.clear();

        assertEquals(403, call("bob", "DELETE", path, null).status);
        assertEquals(204, call("anna", "DELETE", path, null).status);

        assertEquals(404, call("anna", "GET", path, null).status);
        assertEquals(List.of("1795208362999-bbbbbbbb"), ids(call("anna", "GET", "/alerts", null).body));
        assertTrue(alerts.entries.isEmpty());
        // The alert was still open, so the next matching event must open a new one.
        assertEquals(List.of("fingerprint-1795208362373-aaaaaaaa"), alerts.released);
        assertEquals(List.of("alert.deleted OPEN"), published);
        assertEquals(404, call("anna", "DELETE", path, null).status);
        assertEquals(404, call("anna", "DELETE", "/alerts/1795208363000-cccccccc", null).status);
        assertTrue(alerts.alerts.containsKey("1795208363000-cccccccc"));
    }

    @Test
    void adminSetsAnotherMembersNameAndPhone() {
        String code = createOrganization("anna");
        call("bob", "POST", "/orgs/join", "{\"inviteCode\": \"" + code + "\"}");

        JsonNode updated = call("anna", "PATCH", "/members/bob",
                "{\"name\": \" Bob Kowalski \", \"phone\": \"+48600100300\"}").body;

        assertEquals("Bob Kowalski", updated.get("name").asText());
        assertEquals("+48600100300", updated.get("phone").asText());
        assertEquals("user", updated.get("role").asText());
        // Role and details can change in one request, and a blank phone removes the number.
        JsonNode promoted = call("anna", "PATCH", "/members/bob", "{\"role\": \"team_lead\", \"phone\": \"\"}").body;
        assertEquals("team_lead", promoted.get("role").asText());
        assertEquals("Bob Kowalski", promoted.get("name").asText());
        assertFalse(promoted.hasNonNull("phone"));
        assertEquals("Bob Kowalski", call("bob", "GET", "/me", null).body.get("name").asText());

        assertInvalid("international form", call("anna", "PATCH", "/members/bob", "{\"phone\": \"600100300\"}"));
        assertInvalid("Send a role", call("anna", "PATCH", "/members/bob", "{}"));
        assertEquals(403, call("bob", "PATCH", "/members/anna", "{\"name\": \"Changed\"}").status);
        assertEquals(404, call("anna", "PATCH", "/members/stranger", "{\"name\": \"Nobody\"}").status);
    }

    @Test
    void ruleCarriesADeliveryWindowWithAFallbackChannel() {
        createOrganization("anna");
        String sourceId = call("anna", "POST", "/sources", WEBHOOK_SOURCE).body.get("sourceId").asText();
        String rule = "{\"name\": \"Errors\", \"sourceId\": \"" + sourceId + "\", \"severity\": \"WARNING\","
                + " \"match\": {\"mode\": \"all\", \"conditions\": []}, \"delivery\": %s}";
        String window = "{\"window\": {\"from\": \"%s\", \"to\": \"%s\", \"timezone\": \"%s\"}, \"outsideWindow\": %s}";

        Result created = call("anna", "POST", "/rules",
                rule.formatted(window.formatted("08:00", "20:00", "Europe/Warsaw", "\"email\"")));

        assertEquals(201, created.status);
        String ruleId = created.body.get("ruleId").asText();
        JsonNode stored = call("anna", "GET", "/rules/" + ruleId, null).body.get("delivery");
        assertEquals("08:00", stored.get("window").get("from").asText());
        assertEquals("Europe/Warsaw", stored.get("window").get("timezone").asText());
        assertEquals("email", stored.get("outsideWindow").asText());
        // A rule without the setting notifies at any hour.
        assertFalse(call("anna", "PUT", "/rules/" + ruleId, rule.formatted("null")).body.hasNonNull("delivery"));

        assertInvalid("times of day", call("anna", "POST", "/rules",
                rule.formatted(window.formatted("8", "20:00", "Europe/Warsaw", "null"))));
        assertInvalid("must differ", call("anna", "POST", "/rules",
                rule.formatted(window.formatted("08:00", "08:00", "Europe/Warsaw", "null"))));
        assertInvalid("time zone name", call("anna", "POST", "/rules",
                rule.formatted(window.formatted("08:00", "20:00", "Mars/Olympus", "null"))));
        assertInvalid("must be a channel", call("anna", "POST", "/rules",
                rule.formatted(window.formatted("08:00", "20:00", "Europe/Warsaw", "\"pigeon\""))));
        assertInvalid("delivery.window is required", call("anna", "POST", "/rules",
                rule.formatted("{\"outsideWindow\": \"email\"}")));
    }

    @Test
    void requestsOutsideTheRulesGetClearErrors() {
        assertEquals(404, call("anna", "GET", "/nothing-here", null).status);
        // A browser's preflight has no token and must still succeed.
        assertEquals(204, call(null, "OPTIONS", "/alerts", null).status);
        assertEquals(403, call("anna", "GET", "/alerts", null).status);
        assertEquals(401, call(null, "GET", "/me", null).status);
        assertEquals("ok", call("anna", "GET", "/health", null).body.get("status").asText());
        Result forbidden = call("anna", "GET", "/sources", null);
        assertEquals("FORBIDDEN", forbidden.body.get("error").get("code").asText());
    }

    private String createOrganization(String userId) {
        return call(userId, "POST", "/orgs", "{\"name\": \"Cloud team\"}").body.get("inviteCode").asText();
    }

    private static void assertInvalid(String expectedReason, Result result) {
        assertEquals(400, result.status);
        assertEquals("VALIDATION_FAILED", result.body.get("error").get("code").asText());
        String message = result.body.get("error").get("message").asText();
        assertTrue(message.contains(expectedReason), message);
        assertNotEquals("", message);
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(alert -> ids.add(alert.get("alertId").asText()));
        return ids;
    }

    private static Alert alert(String alertId, String orgId, Severity severity) {
        return new Alert(alertId, orgId, "rule-1", "Payment database timeouts", "src-1", "fingerprint-" + alertId,
                severity, Alert.Status.OPEN, "payment-api: DB_TIMEOUT", "Timeout on payment-api-1",
                Map.of("service", "payment-api"), 3, NOW, NOW, null, "rb-1",
                null, null, null, null, null, new Alert.Timing(NOW, NOW), null);
    }

    private record Result(int status, JsonNode body) {
    }

    private Result call(String userId, String method, String pathAndQuery, String body) {
        String[] parts = pathAndQuery.split("\\?", 2);
        Map<String, String> query = new HashMap<>();
        if (parts.length == 2) {
            for (String pair : parts[1].split("&")) {
                String[] keyValue = pair.split("=", 2);
                query.put(keyValue[0], keyValue[1]);
            }
        }
        Map<String, String> claims = new HashMap<>();
        if (userId != null) {
            claims.put("sub", userId);
            claims.put("email", userId + "@example.pl");
            if (organizationInToken.containsKey(userId)) {
                claims.put("custom:orgId", organizationInToken.get(userId));
            }
        }
        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withRawPath(parts[0])
                .withQueryStringParameters(query.isEmpty() ? null : query)
                .withBody(body)
                .withIsBase64Encoded(false)
                .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                        .withHttp(APIGatewayV2HTTPEvent.RequestContext.Http.builder().withMethod(method).build())
                        .withAuthorizer(APIGatewayV2HTTPEvent.RequestContext.Authorizer.builder()
                                .withJwt(APIGatewayV2HTTPEvent.RequestContext.Authorizer.JWT.builder()
                                        .withClaims(claims)
                                        .build())
                                .build())
                        .build())
                .build();
        APIGatewayV2HTTPResponse response = api.handleRequest(event, null);
        return new Result(response.getStatusCode(), response.getBody() == null ? null : Json.tree(response.getBody()));
    }
}
