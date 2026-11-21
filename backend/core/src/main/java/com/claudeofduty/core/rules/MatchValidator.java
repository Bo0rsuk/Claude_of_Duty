package com.claudeofduty.core.rules;

import com.claudeofduty.core.model.Rule.Condition;
import com.claudeofduty.core.model.Rule.Match;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Checks that a rule's conditions can be evaluated, whether they came from a person or from the assistant. */
public final class MatchValidator {

    private MatchValidator() {
    }

    /** @throws IllegalArgumentException naming the first problem found */
    public static void validate(Match match, Operators operators) {
        if (match == null || match.conditions() == null) {
            return;
        }
        for (Condition condition : match.conditions()) {
            if (condition.field() == null || condition.field().isBlank()) {
                throw new IllegalArgumentException("condition field is required");
            }
            if (operators.find(condition.op()).isEmpty()) {
                throw new IllegalArgumentException("Unknown operator: " + condition.op());
            }
            if ("regex".equals(condition.op())) {
                try {
                    Pattern.compile(String.valueOf(condition.value()));
                } catch (PatternSyntaxException e) {
                    throw new IllegalArgumentException(
                            "Invalid regular expression for " + condition.field() + ": " + e.getDescription());
                }
            }
            if ("in".equals(condition.op()) && !(condition.value() instanceof Collection)) {
                throw new IllegalArgumentException("The value of \"in\" for " + condition.field() + " must be a list");
            }
        }
    }
}
