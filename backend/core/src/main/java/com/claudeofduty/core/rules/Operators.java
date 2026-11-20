package com.claudeofduty.core.rules;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;

/** The operators available to rule conditions, by name. Semantics: Docs/contracts/rules.md. */
public final class Operators {

    private final Map<String, Operator> byName = new ConcurrentHashMap<>();
    private final Map<String, Pattern> patterns = new ConcurrentHashMap<>();

    public static Operators withDefaults() {
        Operators operators = new Operators();
        operators.register("eq", Operators::equal);
        operators.register("neq", (actual, expected) -> !equal(actual, expected));
        operators.register("in", (actual, expected) -> expected instanceof Collection<?> options
                && options.stream().anyMatch(option -> equal(actual, option)));
        operators.register("contains", (actual, expected) -> actual != null && expected != null
                && text(actual).contains(text(expected)));
        operators.register("regex", (actual, expected) -> actual != null && expected != null
                && operators.pattern(text(expected)).matcher(text(actual)).find());
        operators.register("gt", numeric(comparison -> comparison > 0));
        operators.register("gte", numeric(comparison -> comparison >= 0));
        operators.register("lt", numeric(comparison -> comparison < 0));
        operators.register("lte", numeric(comparison -> comparison <= 0));
        operators.register("exists", (actual, expected) -> (actual != null) != isFalse(expected));
        return operators;
    }

    public void register(String name, Operator operator) {
        byName.put(name, operator);
    }

    public Optional<Operator> find(String name) {
        return Optional.ofNullable(name).map(byName::get);
    }

    private Pattern pattern(String regex) {
        return patterns.computeIfAbsent(regex, Pattern::compile);
    }

    /** Compares text forms, so the number 503 equals the string "503". */
    private static boolean equal(Object actual, Object expected) {
        return actual != null && expected != null && text(actual).equals(text(expected));
    }

    private static Operator numeric(IntPredicate outcome) {
        return (actual, expected) -> {
            Optional<BigDecimal> left = number(actual);
            Optional<BigDecimal> right = number(expected);
            return left.isPresent() && right.isPresent() && outcome.test(left.get().compareTo(right.get()));
        };
    }

    private static Optional<BigDecimal> number(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(value.toString().trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String text(Object value) {
        return value instanceof Number
                ? new BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
                : value.toString();
    }

    private static boolean isFalse(Object value) {
        return value != null && "false".equalsIgnoreCase(value.toString());
    }
}
