package com.claudeofduty.aws;

import com.claudeofduty.core.port.Throttle;
import java.time.Instant;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

/** Throttling counters in DynamoDB. Each is one item that expires an hour after its window ends. */
public final class DynamoThrottle implements Throttle {

    private static final long KEEP_AFTER_WINDOW_SECONDS = 3600;

    private final DynamoDbClient dynamoDb;

    public DynamoThrottle(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    /** Increment and limit check are one conditional update, so concurrent processors cannot overshoot the limit. */
    @Override
    public boolean tryAcquire(String counterKey, int max, Instant windowEnd) {
        try {
            dynamoDb.updateItem(request -> request
                    .tableName(Tables.COUNTERS)
                    .key(Map.of("counterKey", AttributeValue.fromS(counterKey)))
                    .updateExpression("SET expiresAt = :expiresAt ADD #count :one")
                    .conditionExpression("attribute_not_exists(#count) OR #count < :max")
                    .expressionAttributeNames(Map.of("#count", "count"))
                    .expressionAttributeValues(Map.of(
                            ":one", AttributeValue.fromN("1"),
                            ":max", AttributeValue.fromN(Integer.toString(max)),
                            ":expiresAt", AttributeValue.fromN(
                                    Long.toString(windowEnd.getEpochSecond() + KEEP_AFTER_WINDOW_SECONDS)))));
            return true;
        } catch (ConditionalCheckFailedException limitReached) {
            return false;
        }
    }
}
