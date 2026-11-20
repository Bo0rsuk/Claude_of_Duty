package com.claudeofduty.core.template;

import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders {{name}} placeholders. Unknown names render as an empty string. */
public final class Templates {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^{}\\s]+)\\s*}}");

    private Templates() {
    }

    public static String render(String template, Map<String, Object> variables) {
        if (template == null) {
            return "";
        }
        return PLACEHOLDER.matcher(template)
                .replaceAll(match -> Matcher.quoteReplacement(text(variables.get(match.group(1)))));
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        return value instanceof Number
                ? new BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
                : value.toString();
    }
}
