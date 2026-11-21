package com.claudeofduty.aws;

import com.claudeofduty.core.api.Page;
import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.TimelineEntry;
import com.claudeofduty.core.port.AlertRepository;
import com.claudeofduty.core.port.AlertStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

/** Alerts, their timeline and the deduplication pointers in DynamoDB. */
public final class DynamoAlertStore implements AlertStore, AlertRepository {

    private static final Set<String> KEY_FIELDS = Set.of("orgId", "alertId");
    private static final String COUNT = "count";
    private static final String LAST_SEEN_AT = "lastSeenAt";

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

    /**
     * One UpdateItem: the count is added atomically, and every other field is set
     * only if it is not there yet. Concurrent writers for the same alert therefore
     * end up with one alert and the sum of their counts, whatever order they arrive in.
     */
    @Override
    public Alert addEvents(Alert alert) {
        Map<String, String> names = new HashMap<>();
        Map<String, AttributeValue> values = new HashMap<>();
        List<String> assignments = new ArrayList<>();
        ItemCodec.toItem(alert).forEach((field, value) -> {
            if (KEY_FIELDS.contains(field) || field.equals(COUNT)) {
                return;
            }
            int index = assignments.size();
            names.put("#f" + index, field);
            values.put(":v" + index, value);
            assignments.add(field.equals(LAST_SEEN_AT)
                    ? "#f" + index + " = :v" + index
                    : "#f" + index + " = if_not_exists(#f" + index + ", :v" + index + ")");
        });
        names.put("#count", COUNT);
        values.put(":count", AttributeValue.fromN(Long.toString(alert.count())));

        UpdateItemResponse response = dynamoDb.updateItem(request -> request
                .tableName(Tables.ALERTS)
                .key(alertKey(alert.orgId(), alert.alertId()))
                .updateExpression("SET " + String.join(", ", assignments) + " ADD #count :count")
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .returnValues(ReturnValue.ALL_NEW));
        return ItemCodec.fromItem(response.attributes(), Alert.class);
    }

    @Override
    public void appendTimeline(TimelineEntry entry) {
        dynamoDb.putItem(request -> request.tableName(Tables.ALERT_TIMELINE).item(ItemCodec.toItem(entry)));
    }

    @Override
    public Page<Alert> newest(String orgId, int limit, String cursor) {
        QueryRequest.Builder request = QueryRequest.builder()
                .tableName(Tables.ALERTS)
                .keyConditionExpression("orgId = :orgId")
                .expressionAttributeValues(Map.of(":orgId", AttributeValue.fromS(orgId)))
                // Alert ids start with the creation time, so descending order is newest first.
                .scanIndexForward(false)
                .limit(limit);
        if (cursor != null && !cursor.isBlank()) {
            request.exclusiveStartKey(alertKey(orgId, cursor));
        }
        QueryResponse response = dynamoDb.query(request.build());
        List<Alert> alerts = response.items().stream().map(item -> ItemCodec.fromItem(item, Alert.class)).toList();
        String nextCursor = response.hasLastEvaluatedKey() && !response.lastEvaluatedKey().isEmpty()
                ? response.lastEvaluatedKey().get("alertId").s()
                : null;
        return new Page<>(alerts, nextCursor);
    }

    @Override
    public Optional<Alert> find(String orgId, String alertId) {
        GetItemResponse response = dynamoDb.getItem(request -> request.tableName(Tables.ALERTS).key(alertKey(orgId, alertId)));
        return response.hasItem() ? Optional.of(ItemCodec.fromItem(response.item(), Alert.class)) : Optional.empty();
    }

    @Override
    public Optional<Alert> update(String orgId, String alertId, Map<String, Object> changes) {
        Map<String, String> names = new HashMap<>();
        Map<String, AttributeValue> values = new HashMap<>();
        List<String> assignments = new ArrayList<>();
        // Converted through the model's JSON form, so enums and timestamps are stored as everywhere else.
        ItemCodec.toItem(changes).forEach((field, value) -> {
            int index = assignments.size();
            names.put("#f" + index, field);
            values.put(":v" + index, value);
            assignments.add("#f" + index + " = :v" + index);
        });
        try {
            UpdateItemResponse response = dynamoDb.updateItem(request -> request
                    .tableName(Tables.ALERTS)
                    .key(alertKey(orgId, alertId))
                    .updateExpression("SET " + String.join(", ", assignments))
                    .conditionExpression("attribute_exists(alertId)")
                    .expressionAttributeNames(names)
                    .expressionAttributeValues(values)
                    .returnValues(ReturnValue.ALL_NEW));
            return Optional.of(ItemCodec.fromItem(response.attributes(), Alert.class));
        } catch (ConditionalCheckFailedException noSuchAlert) {
            return Optional.empty();
        }
    }

    @Override
    public List<TimelineEntry> timeline(String alertId) {
        return dynamoDb.queryPaginator(request -> request
                        .tableName(Tables.ALERT_TIMELINE)
                        .keyConditionExpression("alertId = :alertId")
                        .expressionAttributeValues(Map.of(":alertId", AttributeValue.fromS(alertId))))
                .items()
                .stream()
                .map(item -> ItemCodec.fromItem(item, TimelineEntry.class))
                .toList();
    }

    @Override
    public void release(String fingerprint) {
        dynamoDb.deleteItem(request -> request
                .tableName(Tables.DEDUP)
                .key(Map.of("fingerprint", AttributeValue.fromS(fingerprint))));
    }

    private static Map<String, AttributeValue> alertKey(String orgId, String alertId) {
        return Map.of("orgId", AttributeValue.fromS(orgId), "alertId", AttributeValue.fromS(alertId));
    }
}
