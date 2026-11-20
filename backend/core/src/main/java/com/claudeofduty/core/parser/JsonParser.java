package com.claudeofduty.core.parser;

import com.claudeofduty.core.json.Json;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a JSON object. Nested objects are flattened with dots ("http.status").
 * With a split path, each element of that array becomes its own event that
 * also carries the parent's other fields.
 */
final class JsonParser implements Parser {

    private final String splitPath;

    JsonParser(String splitPath) {
        this.splitPath = splitPath;
    }

    @Override
    public List<Map<String, Object>> parse(String payload) {
        JsonNode root = read(payload);
        Map<String, Object> fields = new LinkedHashMap<>();
        flatten(root, "", fields);

        JsonNode elements = splitPath == null ? null : at(root, splitPath);
        if (elements == null || !elements.isArray()) {
            return List.of(fields);
        }
        fields.remove(splitPath);
        List<Map<String, Object>> events = new ArrayList<>();
        for (JsonNode element : elements) {
            Map<String, Object> event = new LinkedHashMap<>(fields);
            flatten(element, "", event);
            events.add(event);
        }
        return events;
    }

    private static JsonNode read(String payload) {
        try {
            JsonNode root = Json.tree(payload);
            if (!root.isObject()) {
                throw new UnparsablePayloadException("Payload is not a JSON object");
            }
            return root;
        } catch (UncheckedIOException e) {
            throw new UnparsablePayloadException("Payload is not valid JSON");
        }
    }

    private static JsonNode at(JsonNode root, String path) {
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            current = current.path(part);
        }
        return current;
    }

    private static void flatten(JsonNode node, String prefix, Map<String, Object> out) {
        node.fields().forEachRemaining(entry -> {
            String key = prefix + entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isObject()) {
                flatten(value, key + ".", out);
            } else if (value.isArray()) {
                out.put(key, value.toString());
            } else if (value.isNumber()) {
                out.put(key, value.numberValue());
            } else if (value.isBoolean()) {
                out.put(key, value.booleanValue());
            } else if (!value.isNull()) {
                out.put(key, value.asText());
            }
        });
    }
}
