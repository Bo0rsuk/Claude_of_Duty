package com.claudeofduty.aws;

import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.port.SourceDirectory;
import java.util.List;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/** Reads all sources with a table scan. The worker does this every few seconds, and the table is small. */
public final class DynamoSourceDirectory implements SourceDirectory {

    private final DynamoDbClient dynamoDb;

    public DynamoSourceDirectory(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public List<Source> all() {
        return dynamoDb.scanPaginator(request -> request.tableName(Tables.SOURCES))
                .items()
                .stream()
                .map(item -> ItemCodec.fromItem(item, Source.class))
                .toList();
    }
}
