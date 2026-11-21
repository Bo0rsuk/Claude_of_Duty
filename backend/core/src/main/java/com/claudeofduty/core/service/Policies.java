package com.claudeofduty.core.service;

import com.claudeofduty.core.api.ApiException;
import com.claudeofduty.core.model.RecipientGroup;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Runbook;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.parser.ParserRegistry;
import com.claudeofduty.core.port.Repository;
import com.claudeofduty.core.rules.MatchValidator;
import com.claudeofduty.core.rules.Operators;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Set;

/** What makes each kind of configuration object valid, and its side effects. */
public final class Policies {

    private static final Set<String> CONNECTOR_TYPES = Set.of(Source.WEBHOOK, Source.HTTP_PULL, Source.KAFKA);
    private static final Set<String> RUNBOOK_FORMATS =
            Set.of(Runbook.MARKDOWN, Runbook.TEXT, Runbook.LINK, Runbook.CHECKLIST);

    private Policies() {
    }

    public static EntityPolicy<Source> sources(ParserRegistry parsers, Repository<Rule> rules) {
        return new EntityPolicy<>() {
            private final SecureRandom random = new SecureRandom();

            /** A webhook source keeps its token across edits and gets one when it has none. */
            @Override
            public void applyDefaults(ObjectNode body, Source existing) {
                if (!body.has("enabled")) {
                    body.put("enabled", true);
                }
                boolean webhook = Source.WEBHOOK.equals(body.path("connector").path("type").asText());
                if (webhook && body.path("ingestToken").asText("").isBlank()) {
                    byte[] token = new byte[16];
                    random.nextBytes(token);
                    body.put("ingestToken", existing != null && existing.ingestToken() != null
                            ? existing.ingestToken()
                            : HexFormat.of().formatHex(token));
                }
            }

            @Override
            public void validate(Source source) {
                requireText(source.name(), "name");
                if (source.connector() == null || !CONNECTOR_TYPES.contains(source.connector().type())) {
                    throw ApiException.invalid("connector.type must be one of " + CONNECTOR_TYPES);
                }
                if (source.parser() != null && source.parser().format() != null) {
                    try {
                        parsers.create(source.parser());
                    } catch (IllegalArgumentException e) {
                        throw ApiException.invalid("parser: " + e.getMessage());
                    }
                }
            }

            /** Rules cannot outlive the source whose events they match. */
            @Override
            public void afterDelete(String orgId, String sourceId) {
                rules.list(orgId).stream()
                        .filter(rule -> sourceId.equals(rule.sourceId()))
                        .forEach(rule -> rules.delete(orgId, rule.ruleId()));
            }
        };
    }

    public static EntityPolicy<Rule> rules(Operators operators, Repository<Source> sources) {
        return new EntityPolicy<>() {
            @Override
            public void applyDefaults(ObjectNode body, Rule existing) {
                if (!body.has("enabled")) {
                    body.put("enabled", true);
                }
            }

            @Override
            public void validate(Rule rule) {
                requireText(rule.name(), "name");
                if (rule.severity() == null) {
                    throw ApiException.invalid("severity must be one of CRITICAL, WARNING, INFO");
                }
                if (rule.sourceId() == null || sources.find(rule.orgId(), rule.sourceId()).isEmpty()) {
                    throw ApiException.invalid("sourceId must refer to an existing source");
                }
                try {
                    MatchValidator.validate(rule.match(), operators);
                } catch (IllegalArgumentException e) {
                    throw ApiException.invalid(e.getMessage());
                }
            }
        };
    }

    public static EntityPolicy<RecipientGroup> groups() {
        return group -> requireText(group.name(), "name");
    }

    public static EntityPolicy<Runbook> runbooks() {
        return runbook -> {
            requireText(runbook.title(), "title");
            if (!RUNBOOK_FORMATS.contains(runbook.format())) {
                throw ApiException.invalid("format must be one of " + RUNBOOK_FORMATS);
            }
        };
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw ApiException.invalid(name + " is required");
        }
    }
}
