package com.claudeofduty.core.alerting;

import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.rules.EventFields;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Identifies which alert an event belongs to: same rule and same values of the rule's groupBy fields. */
public final class Fingerprint {

    private static final char SEPARATOR = '\u0000';

    private Fingerprint() {
    }

    public static String of(Rule rule, NormalizedEvent event) {
        StringBuilder key = new StringBuilder(rule.orgId()).append(SEPARATOR).append(rule.ruleId());
        List<String> groupBy = rule.groupBy() == null ? List.of() : rule.groupBy();
        for (String field : groupBy) {
            Object value = EventFields.value(event, field);
            key.append(SEPARATOR).append(value == null ? "" : value);
        }
        return sha256(key.toString());
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
