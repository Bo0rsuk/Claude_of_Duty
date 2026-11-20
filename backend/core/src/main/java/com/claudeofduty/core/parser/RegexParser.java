package com.claudeofduty.core.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses one line of text. Every named group of the pattern becomes a field. */
final class RegexParser implements Parser {

    private final Pattern pattern;

    RegexParser(String pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("A regex parser needs a pattern");
        }
        this.pattern = Pattern.compile(pattern);
    }

    @Override
    public List<Map<String, Object>> parse(String payload) {
        Matcher matcher = pattern.matcher(payload.strip());
        if (!matcher.find()) {
            throw new UnparsablePayloadException("Payload does not match the pattern");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String group : pattern.namedGroups().keySet()) {
            String value = matcher.group(group);
            if (value != null) {
                fields.put(group, value);
            }
        }
        return List.of(fields);
    }
}
