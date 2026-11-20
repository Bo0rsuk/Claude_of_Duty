package com.claudeofduty.core.rules;

import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Rule.Condition;
import com.claudeofduty.core.model.Rule.MatchMode;
import java.util.List;

/** Decides whether an event satisfies a rule's conditions. */
public final class RuleMatcher {

    private final Operators operators;

    public RuleMatcher(Operators operators) {
        this.operators = operators;
    }

    public boolean matches(Rule rule, NormalizedEvent event) {
        List<Condition> conditions = rule.match() == null ? null : rule.match().conditions();
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        return rule.match().mode() == MatchMode.ANY
                ? conditions.stream().anyMatch(condition -> holds(condition, event))
                : conditions.stream().allMatch(condition -> holds(condition, event));
    }

    /** A condition with an unknown operator or a broken regex never holds, instead of failing the batch. */
    private boolean holds(Condition condition, NormalizedEvent event) {
        if (condition.field() == null) {
            return false;
        }
        try {
            return operators.find(condition.op())
                    .map(operator -> operator.test(EventFields.value(event, condition.field()), condition.value()))
                    .orElse(false);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
