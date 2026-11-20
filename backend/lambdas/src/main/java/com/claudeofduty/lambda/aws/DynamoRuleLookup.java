package com.claudeofduty.lambda.aws;

import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.port.RuleLookup;
import com.claudeofduty.core.util.TtlCache;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Reads an organization's rules from DynamoDB and remembers them for a few
 * seconds. That delay is how long a rule change made in the UI takes to apply.
 */
public final class DynamoRuleLookup implements RuleLookup {

    private static final Duration CACHE_TTL = Duration.ofSeconds(15);

    private final DynamoDbClient dynamoDb;
    private final TtlCache<String, List<Rule>> rulesByOrg = new TtlCache<>(CACHE_TTL);

    public DynamoRuleLookup(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public List<Rule> enabledRulesFor(String orgId, String sourceId) {
        return rulesByOrg.get(orgId, this::load).stream()
                .filter(rule -> rule.enabled() && sourceId.equals(rule.sourceId()))
                .toList();
    }

    private List<Rule> load(String orgId) {
        return dynamoDb.queryPaginator(request -> request
                        .tableName(Tables.RULES)
                        .keyConditionExpression("orgId = :orgId")
                        .expressionAttributeValues(Map.of(":orgId", AttributeValue.fromS(orgId))))
                .items()
                .stream()
                .map(item -> ItemCodec.fromItem(item, Rule.class))
                .toList();
    }
}
