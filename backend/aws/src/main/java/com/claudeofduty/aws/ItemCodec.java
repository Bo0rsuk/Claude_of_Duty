package com.claudeofduty.aws;

import com.claudeofduty.core.json.Json;
import java.util.Map;
import software.amazon.awssdk.enhanced.dynamodb.document.EnhancedDocument;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Stores model objects in DynamoDB in exactly their JSON shape, so the tables,
 * the API and the contracts all show the same field names and values.
 */
public final class ItemCodec {

    private ItemCodec() {
    }

    public static Map<String, AttributeValue> toItem(Object value) {
        return EnhancedDocument.fromJson(Json.write(value)).toMap();
    }

    public static <T> T fromItem(Map<String, AttributeValue> item, Class<T> type) {
        return Json.read(EnhancedDocument.fromAttributeValueMap(item).toJson(), type);
    }
}
