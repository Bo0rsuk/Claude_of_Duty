package com.claudeofduty.core.rules;

/**
 * One comparison usable in a rule condition.
 * To add an operator, implement this and register it in {@link Operators}.
 */
@FunctionalInterface
public interface Operator {

    /**
     * @param actual   the event's value for the condition's field, or null when the event has no such field
     * @param expected the condition's value
     */
    boolean test(Object actual, Object expected);
}
