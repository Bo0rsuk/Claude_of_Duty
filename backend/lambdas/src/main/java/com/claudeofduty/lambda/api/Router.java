package com.claudeofduty.lambda.api;

import com.claudeofduty.core.model.Member.Role;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Maps a method and path to a handler. Patterns use {name} for path parameters. */
public final class Router {

    @FunctionalInterface
    public interface Handler {
        Response handle(Request request);
    }

    /** {@code minimumRole} null means any signed-in user, even one without an organization. */
    public record Route(String method, List<String> segments, Role minimumRole, Handler handler) {
    }

    public record Match(Route route, Map<String, String> pathParameters) {
    }

    private final List<Route> routes = new ArrayList<>();

    public Router add(String method, String pattern, Role minimumRole, Handler handler) {
        routes.add(new Route(method, segments(pattern), minimumRole, handler));
        return this;
    }

    /** Routes are tried in the order they were added, so literal paths go before parameterized ones. */
    public Optional<Match> match(String method, String path) {
        List<String> actual = segments(path);
        for (Route route : routes) {
            if (!route.method().equals(method) || route.segments().size() != actual.size()) {
                continue;
            }
            Map<String, String> parameters = new HashMap<>();
            if (matches(route.segments(), actual, parameters)) {
                return Optional.of(new Match(route, parameters));
            }
        }
        return Optional.empty();
    }

    private static boolean matches(List<String> pattern, List<String> actual, Map<String, String> parameters) {
        for (int i = 0; i < pattern.size(); i++) {
            String expected = pattern.get(i);
            if (expected.startsWith("{") && expected.endsWith("}")) {
                parameters.put(expected.substring(1, expected.length() - 1), actual.get(i));
            } else if (!expected.equals(actual.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> segments(String path) {
        return List.of(path.split("/")).stream().filter(segment -> !segment.isEmpty()).toList();
    }
}
