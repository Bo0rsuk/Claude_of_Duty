package com.claudeofduty.aws;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.port.RecentEvents;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

/**
 * The recent-events sample in DynamoDB. The partition key holds
 * "orgId/sourceId", the sort key starts with the event time, and items expire
 * by TTL after an hour.
 */
public final class DynamoRecentEvents implements RecentEvents {

    /** The most items one BatchWriteItem call accepts. */
    private static final int MAX_BATCH = 25;
    private static final long KEEP_SECONDS = 3600;

    private final DynamoDbClient dynamoDb;
    private final Clock clock;

    public DynamoRecentEvents(DynamoDbClient dynamoDb, Clock clock) {
        this.dynamoDb = dynamoDb;
        this.clock = clock;
    }

    @Override
    public void saveAll(List<NormalizedEvent> events) {
        long expiresAt = clock.instant().getEpochSecond() + KEEP_SECONDS;
        List<WriteRequest> writes = new ArrayList<>();
        for (NormalizedEvent event : events) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sourceId", partition(event.orgId(), event.sourceId()));
            item.put("eventKey", Json.timestamp(event.eventTime()) + "#" + event.eventId());
            item.put("expiresAt", expiresAt);
            item.put("event", event);
            writes.add(WriteRequest.builder().putRequest(put -> put.item(ItemCodec.toItem(item))).build());
        }
        for (int start = 0; start < writes.size(); start += MAX_BATCH) {
            List<WriteRequest> batch = writes.subList(start, Math.min(start + MAX_BATCH, writes.size()));
            // Items DynamoDB leaves unprocessed are dropped: this is a sample, not a record of every event.
            dynamoDb.batchWriteItem(request -> request.requestItems(Map.of(Tables.RECENT_EVENTS, batch)));
        }
    }

    @Override
    public List<NormalizedEvent> latest(String orgId, String sourceId, int limit) {
        return dynamoDb.query(request -> request
                        .tableName(Tables.RECENT_EVENTS)
                        .keyConditionExpression("sourceId = :partition")
                        .expressionAttributeValues(Map.of(":partition", AttributeValue.fromS(partition(orgId, sourceId))))
                        .scanIndexForward(false)
                        .limit(limit))
                .items()
                .stream()
                .map(item -> ItemCodec.fromItem(item.get("event").m(), NormalizedEvent.class))
                .toList();
    }

    private static String partition(String orgId, String sourceId) {
        return orgId + "/" + sourceId;
    }
}
