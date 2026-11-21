package com.claudeofduty.core.notify;

import com.claudeofduty.core.model.NotificationJob;
import com.claudeofduty.core.model.Rule.Escalation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** The order in which a rule's channels are tried when it escalates. Semantics are in Docs/contracts/rules.md. */
public final class Escalations {

    /** The longest a queue can hold a message back. */
    public static final int MAX_DELAY_SECONDS = 900;
    public static final int MIN_STEP_SECONDS = 5;
    public static final int MAX_STEP_SECONDS = 300;

    /** From the least to the most intrusive. Channels on the same level go out together. */
    private static final Map<String, Integer> LEVELS = Map.of("ui", 0, "push", 0, "email", 1, "sms", 2, "voice", 3);

    private Escalations() {
    }

    /** @throws IllegalArgumentException with the reason, when the setting cannot be used */
    public static void validate(Escalation escalation) {
        if (escalation != null
                && (escalation.stepSeconds() < MIN_STEP_SECONDS || escalation.stepSeconds() > MAX_STEP_SECONDS)) {
            throw new IllegalArgumentException("escalation.stepSeconds must be between " + MIN_STEP_SECONDS + " and "
                    + MAX_STEP_SECONDS);
        }
    }

    /**
     * How long each job waits. Only the levels the jobs actually use count, so a rule with
     * in-app and phone calls rings one step after the pop-up, not three.
     *
     * @return seconds of delay per job, in the order given; all zero without an escalation
     */
    public static Map<NotificationJob, Integer> delays(List<NotificationJob> jobs, Escalation escalation) {
        Map<NotificationJob, Integer> delays = new LinkedHashMap<>();
        List<Integer> used = new TreeSet<>(jobs.stream().map(job -> level(job.channel())).toList()).stream().toList();
        for (NotificationJob job : jobs) {
            int step = escalation == null ? 0 : used.indexOf(level(job.channel()));
            delays.put(job, escalation == null ? 0 : Math.min(MAX_DELAY_SECONDS, step * escalation.stepSeconds()));
        }
        return delays;
    }

    private static int level(String channel) {
        return LEVELS.getOrDefault(channel, 0);
    }
}
