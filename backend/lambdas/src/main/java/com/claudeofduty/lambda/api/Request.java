package com.claudeofduty.lambda.api;

import com.claudeofduty.core.api.ApiException;
import com.claudeofduty.core.api.Caller;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.function.Function;

/** A request after routing and authentication. */
public record Request(Caller caller, Map<String, String> path, Map<String, String> query, JsonNode body) {

    public String path(String name) {
        return path.get(name);
    }

    /** @return the query parameter, or null when absent */
    public String query(String name) {
        return query.get(name);
    }

    /** @return the parameter converted, or null when absent */
    public <T> T query(String name, Function<String, T> conversion) {
        String value = query.get(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return conversion.apply(value);
        } catch (RuntimeException e) {
            throw ApiException.invalid("Invalid value for " + name + ": " + value);
        }
    }

    /** @return a text field of the JSON body, or null when absent */
    public String text(String field) {
        JsonNode value = body == null ? null : body.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
