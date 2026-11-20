package com.claudeofduty.lambda.aws;

import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.port.SourceLookup;
import com.claudeofduty.core.util.TtlCache;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

/** Reads sources from DynamoDB and remembers them briefly, misses included. */
public final class DynamoSourceLookup implements SourceLookup {

    private static final Duration CACHE_TTL = Duration.ofSeconds(15);

    private record Key(String orgId, String sourceId) {
    }

    private final DynamoDbClient dynamoDb;
    private final TtlCache<Key, Optional<Source>> cache = new TtlCache<>(CACHE_TTL);

    public DynamoSourceLookup(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public Optional<Source> find(String orgId, String sourceId) {
        return cache.get(new Key(orgId, sourceId), this::load);
    }

    private Optional<Source> load(Key key) {
        GetItemResponse response = dynamoDb.getItem(request -> request
                .tableName(Tables.SOURCES)
                .key(Map.of(
                        "orgId", AttributeValue.fromS(key.orgId()),
                        "sourceId", AttributeValue.fromS(key.sourceId()))));
        return response.hasItem() ? Optional.of(ItemCodec.fromItem(response.item(), Source.class)) : Optional.empty();
    }
}
