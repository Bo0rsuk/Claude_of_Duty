package com.claudeofduty.aws;

import com.claudeofduty.core.port.Repository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

/** A table keyed by orgId and the object's own id, holding objects in their JSON shape. */
public final class DynamoRepository<T> implements Repository<T> {

    private final DynamoDbClient dynamoDb;
    private final String table;
    private final String idAttribute;
    private final Class<T> type;

    public DynamoRepository(DynamoDbClient dynamoDb, String table, String idAttribute, Class<T> type) {
        this.dynamoDb = dynamoDb;
        this.table = table;
        this.idAttribute = idAttribute;
        this.type = type;
    }

    @Override
    public List<T> list(String orgId) {
        return dynamoDb.queryPaginator(request -> request
                        .tableName(table)
                        .keyConditionExpression("orgId = :orgId")
                        .expressionAttributeValues(Map.of(":orgId", AttributeValue.fromS(orgId))))
                .items()
                .stream()
                .map(item -> ItemCodec.fromItem(item, type))
                .toList();
    }

    @Override
    public Optional<T> find(String orgId, String id) {
        GetItemResponse response = dynamoDb.getItem(request -> request.tableName(table).key(key(orgId, id)));
        return response.hasItem() ? Optional.of(ItemCodec.fromItem(response.item(), type)) : Optional.empty();
    }

    @Override
    public void save(T item) {
        dynamoDb.putItem(request -> request.tableName(table).item(ItemCodec.toItem(item)));
    }

    @Override
    public void delete(String orgId, String id) {
        dynamoDb.deleteItem(request -> request.tableName(table).key(key(orgId, id)));
    }

    private Map<String, AttributeValue> key(String orgId, String id) {
        return Map.of("orgId", AttributeValue.fromS(orgId), idAttribute, AttributeValue.fromS(id));
    }
}
