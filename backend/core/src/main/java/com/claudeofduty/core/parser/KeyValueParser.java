package com.claudeofduty.core.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a line of key=value pairs, e.g. {@code service=settlement level=error msg="queue is full"}.
 * A value with spaces is written in double quotes.
 */
final class KeyValueParser implements Parser {

    private static final Pattern PAIR = Pattern.compile("([\\w.-]+)=(\"((?:[^\"\\\\]|\\\\.)*)\"|\\S*)");

    @Override
    public List<ParsedEvent> parse(String payload) {
        Map<String, Object> fields = new LinkedHashMap<>();
        Matcher pairs = PAIR.matcher(payload);
        while (pairs.find()) {
            String quoted = pairs.group(3);
            fields.put(pairs.group(1), quoted != null ? quoted.replace("\\\"", "\"") : pairs.group(2));
        }
        if (fields.isEmpty()) {
            throw new UnparsablePayloadException("Payload has no key=value pairs");
        }
        return List.of(new ParsedEvent(fields, payload));
    }
}
