package com.claudeofduty.lambda.aws;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.TimelineEntry;
import com.claudeofduty.core.port.AlertStore;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

/** Alerts, their timeline and the deduplication pointers in DynamoDB. */
public final class DynamoAlertStore implements AlertStore {

    private final DynamoDbClient dynamoDb;

    public DynamoAlertStore(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    /** A conditional write: of all concurrent callers with the same fingerprint, exactly one registers its id. */
    @Override
    public String claim(String orgId, String fingerprint, String candidateAlertId) {
        try {
            dynamoDb.putItem(request -> request
                    .tableName(Tables.DEDUP)
                    .item(Map.of(
                            "fingerprint", AttributeValue.fromS(fingerprint),
                            "alertId", AttributeValue.fromS(candidateAlertId),
                            "orgId", AttributeValue.fromS(orgId)))
                    .conditionExpression("attribute_not_exists(fingerprint)")
                    .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD));
            return candidateAlertId;
        } catch (ConditionalCheckFailedException alreadyClaimed) {
            return alreadyClaimed.item().get("alertId").s();
        }
    }

    @Override
    public void create(Alert alert) {
        dynamoDb.putItem(request -> request.tableName(Tables.ALERTS).item(ItemCodec.toItem(alert)));
    }

    /** An atomic counter update, so concurrent batches never lose events. */
    @Override
    public Optional<Alert> addEvents(String orgId, String alertId, long count, Instant lastSeenAt) {
        try {
            UpdateItemResponse response = dynamoDb.updateItem(request -> request
                    .tableName(Tables.ALERTS)
                    .key(Map.of("orgId", AttributeValue.fromS(orgId), "alertId", AttributeValue.fromS(alertId)))
                    .updateExpression("SET lastSeenAt = :lastSeenAt ADD #count :count")
                    .conditionExpression("attribute_exists(alertId)")
                    .expressionAttributeNames(Map.of("#count", "count"))
                    .expressionAttributeValues(Map.of(
                            ":lastSeenAt", AttributeValue.fromS(Json.timestamp(lastSeenAt)),
                            ":count", AttributeValue.fromN(Long.toString(count))))
                    .returnValues(ReturnValue.ALL_NEW));
            return Optional.of(ItemCodec.fromItem(response.attributes(), Alert.class));
        } catch (ConditionalCheckFailedException noSuchAlert) {
            return Optional.empty();
        }
    }

    @Override
    public void appendTimeline(TimelineEntry entry) {
        dynamoDb.putItem(request -> request.tableName(Tables.ALERT_TIMELINE).item(ItemCodec.toItem(entry)));
    }
}
