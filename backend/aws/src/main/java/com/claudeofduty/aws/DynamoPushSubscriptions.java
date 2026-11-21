package com.claudeofduty.aws;

import com.claudeofduty.core.model.PushSubscription;
import com.claudeofduty.core.port.PushSubscriptions;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** Push subscriptions in DynamoDB, keyed by user and device. */
public final class DynamoPushSubscriptions implements PushSubscriptions {

    private final DynamoDbClient dynamoDb;

    public DynamoPushSubscriptions(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public List<PushSubscription> of(String userId) {
        return dynamoDb.queryPaginator(request -> request
                        .tableName(Tables.PUSH_SUBSCRIPTIONS)
                        .keyConditionExpression("userId = :userId")
                        .expressionAttributeValues(Map.of(":userId", AttributeValue.fromS(userId))))
                .items()
                .stream()
                .map(item -> ItemCodec.fromItem(item, PushSubscription.class))
                .toList();
    }

    @Override
    public void save(PushSubscription subscription) {
        dynamoDb.putItem(request -> request.tableName(Tables.PUSH_SUBSCRIPTIONS).item(ItemCodec.toItem(subscription)));
    }

    @Override
    public void delete(String userId, String subscriptionId) {
        dynamoDb.deleteItem(request -> request.tableName(Tables.PUSH_SUBSCRIPTIONS).key(Map.of(
                "userId", AttributeValue.fromS(userId), "subscriptionId", AttributeValue.fromS(subscriptionId))));
    }
}
