package com.claudeofduty.lambda.api;

import static com.claudeofduty.core.model.Member.Role.ADMIN;
import static com.claudeofduty.core.model.Member.Role.TEAM_LEAD;
import static com.claudeofduty.core.model.Member.Role.USER;

import com.claudeofduty.core.api.ApiException;
import com.claudeofduty.core.api.Page;
import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.Member.Role;
import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.Rule.Match;
import com.claudeofduty.core.model.Severity;
import com.claudeofduty.core.model.Source.ParserConfig;
import com.claudeofduty.core.service.AlertService;
import com.claudeofduty.core.service.ConfigService;
import com.claudeofduty.core.service.PreviewService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The REST API as one table: method, path, minimum role, action.
 * Shapes and semantics are in Docs/contracts/api.md.
 */
public final class ApiRoutes {

    private ApiRoutes() {
    }

    public static Router create(Services services) {
        Router router = new Router();
        var organizations = services.organizations();
        var alerts = services.alerts();

        router.add("GET", "/health", null, request -> Response.ok(Map.of("status", "ok")));

        // Account and organization
        router.add("GET", "/me", null, request -> Response.ok(organizations.profile(request.caller())));
        router.add("PATCH", "/me", USER, request -> Response.ok(
                organizations.updateProfile(request.caller(), request.text("name"), request.text("phone"))));
        router.add("POST", "/orgs/join", null, request -> Response.ok(
                organizations.join(request.caller(), request.text("inviteCode"))));
        router.add("POST", "/orgs", null, request -> Response.created(
                organizations.create(request.caller(), request.text("name"))));
        router.add("GET", "/org", USER, request -> Response.ok(organizations.organization(request.caller())));
        router.add("POST", "/org/invite-code", ADMIN, request -> Response.ok(
                organizations.regenerateInviteCode(request.caller())));
        router.add("GET", "/members", USER, request -> Response.ok(Page.of(organizations.members(request.caller()))));
        router.add("POST", "/members", ADMIN, request -> Response.created(organizations.invite(request.caller(),
                request.text("email"), request.text("name"),
                request.text("role") == null ? null : role(request.text("role")))));
        router.add("PATCH", "/members/{userId}", ADMIN, request -> Response.ok(
                organizations.updateMember(request.caller(), request.path("userId"), role(request.text("role")),
                        request.text("name"), request.text("phone"))));
        // Push notifications: the devices of the signed-in user
        router.add("GET", "/push/public-key", null, request -> Response.ok(
                Map.of("publicKey", services.push().publicKey())));
        router.add("GET", "/me/push-subscriptions", null, request -> Response.ok(
                Page.of(services.push().list(request.caller()))));
        router.add("POST", "/me/push-subscriptions", null, request -> Response.created(
                services.push().register(request.caller(), request.body())));
        router.add("DELETE", "/me/push-subscriptions/{subscriptionId}", null, request -> {
            services.push().remove(request.caller(), request.path("subscriptionId"));
            return Response.noContent();
        });

        router.add("DELETE", "/members/{userId}", ADMIN, request -> {
            organizations.remove(request.caller(), request.path("userId"));
            return Response.noContent();
        });

        // Sources
        router.add("GET", "/source-presets", USER, request -> Response.ok(Page.of(PreviewService.presets())));
        router.add("POST", "/sources/preview", TEAM_LEAD, request -> Response.ok(Map.of("events",
                services.preview().parse(part(request, "parser", ParserConfig.class), request.text("sample")))));
        router.add("GET", "/sources/{sourceId}/events", USER, request -> Response.ok(Page.of(
                services.rulePreviews().events(request.caller(), request.path("sourceId"),
                        request.query("limit", Integer::valueOf)))));
        crud(router, "/sources", "sourceId", services.sources());

        // Rules
        router.add("GET", "/rules", USER, request -> Response.ok(Page.of(services.rules().list(request.caller()).stream()
                .filter(rule -> request.query("sourceId") == null || request.query("sourceId").equals(rule.sourceId()))
                .toList())));
        router.add("POST", "/rules/preview", TEAM_LEAD, request -> Response.ok(services.rulePreviews().preview(
                request.caller(), request.text("sourceId"), part(request, "match", Match.class))));
        router.add("POST", "/ai/rule-suggestion", TEAM_LEAD, request -> Response.ok(services.assistant().suggest(
                request.caller(), request.text("sourceId"), part(request, "sampleEvent", NormalizedEvent.class),
                request.text("goal"))));
        crud(router, "/rules", "ruleId", services.rules());

        // Groups and runbooks
        crud(router, "/groups", "groupId", services.groups());
        crud(router, "/runbooks", "runbookId", services.runbooks());

        // Alerts
        router.add("GET", "/alerts", USER, request -> Response.ok(alerts.list(
                request.caller(),
                new AlertService.Filter(
                        request.query("status", Alert.Status::valueOf),
                        request.query("severity", Severity::valueOf),
                        request.query("ruleId")),
                request.query("limit", Integer::valueOf),
                request.query("cursor"))));
        router.add("GET", "/alerts/{alertId}", USER, request -> Response.ok(
                alerts.get(request.caller(), request.path("alertId"))));
        router.add("GET", "/alerts/{alertId}/timeline", USER, request -> Response.ok(Page.of(
                alerts.timeline(request.caller(), request.path("alertId")))));
        router.add("POST", "/alerts/{alertId}/ack", USER, request -> Response.ok(
                alerts.acknowledge(request.caller(), request.path("alertId"))));
        router.add("POST", "/alerts/{alertId}/resolve", USER, request -> Response.ok(
                alerts.resolve(request.caller(), request.path("alertId"))));
        router.add("POST", "/alerts/{alertId}/assign", USER, request -> Response.ok(
                alerts.assign(request.caller(), request.path("alertId"), request.text("userId"))));
        router.add("POST", "/alerts/{alertId}/notes", USER, request -> Response.ok(
                alerts.addNote(request.caller(), request.path("alertId"), request.text("text"))));
        router.add("POST", "/alerts/{alertId}/resend", TEAM_LEAD, request -> Response.ok(
                alerts.resend(request.caller(), request.path("alertId"))));
        router.add("PUT", "/alerts/{alertId}/runbook-steps", USER, request -> Response.ok(
                alerts.checkSteps(request.caller(), request.path("alertId"), steps(request))));
        router.add("DELETE", "/alerts/{alertId}", TEAM_LEAD, request -> {
            alerts.delete(request.caller(), request.path("alertId"));
            return Response.noContent();
        });

        return router;
    }

    /** Everyone in the organization reads; team leads and admins write. */
    private static <T> void crud(Router router, String base, String idParameter, ConfigService<T> service) {
        String item = base + "/{" + idParameter + "}";
        // A GET /rules route with filtering may already be registered; the first match wins.
        router.add("GET", base, USER, request -> Response.ok(Page.of(service.list(request.caller()))));
        router.add("POST", base, TEAM_LEAD, request -> Response.created(service.create(request.caller(), request.body())));
        router.add("GET", item, USER, request -> Response.ok(service.get(request.caller(), request.path(idParameter))));
        router.add("PUT", item, TEAM_LEAD, request -> Response.ok(
                service.replace(request.caller(), request.path(idParameter), request.body())));
        router.add("DELETE", item, TEAM_LEAD, request -> {
            service.delete(request.caller(), request.path(idParameter));
            return Response.noContent();
        });
    }

    private static Role role(String value) {
        try {
            return value == null ? null : Json.convert(value, Role.class);
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid("role must be one of user, team_lead, admin");
        }
    }

    /** @return the "checked" list of the body, or null when it is missing or is not a list of whole numbers */
    private static List<Integer> steps(Request request) {
        JsonNode checked = request.body() == null ? null : request.body().get("checked");
        if (checked == null || !checked.isArray()) {
            return null;
        }
        List<Integer> steps = new ArrayList<>();
        for (JsonNode step : checked) {
            steps.add(step.isIntegralNumber() ? step.asInt() : null);
        }
        return steps;
    }

    /** @return a part of the JSON body mapped onto a model type, or null when the body does not have it */
    private static <T> T part(Request request, String field, Class<T> type) {
        if (request.body() == null || !request.body().hasNonNull(field)) {
            return null;
        }
        try {
            return Json.convert(request.body().get(field), type);
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid(field + " does not have the expected shape");
        }
    }
}
