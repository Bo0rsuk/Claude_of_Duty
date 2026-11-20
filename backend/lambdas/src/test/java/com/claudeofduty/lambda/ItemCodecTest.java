package com.claudeofduty.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.claudeofduty.core.model.Level;
import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.lambda.aws.ItemCodec;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class ItemCodecTest {

    private static final Instant NOW = Instant.parse("2026-11-20T21:00:00Z");

    @Test
    void storesModelObjectsInTheirJsonShape() {
        Source.Connector connector = new Source.Connector(Source.WEBHOOK, null, null, null, null, null, null, null, null);
        Source source = new Source("src-1", "org-1", "Monitoring", true, connector, null, "secret", NOW, NOW);

        Map<String, AttributeValue> item = ItemCodec.toItem(source);

        assertEquals("org-1", item.get("orgId").s());
        assertEquals("src-1", item.get("sourceId").s());
        assertTrue(item.get("enabled").bool());
        assertEquals("webhook", item.get("connector").m().get("type").s());
        assertEquals("2026-11-20T21:00:00.000Z", item.get("createdAt").s());
        assertEquals(source, ItemCodec.fromItem(item, Source.class));
    }

    @Test
    void keepsNumbersBooleansAndNullsThroughARoundTrip() {
        NormalizedEvent event = new NormalizedEvent("evt-1", "org-1", "src-1", NOW, NOW, Level.ERROR, "Timeout",
                Map.of("http.status", 503, "requestTime", 0.216, "retried", true, "service", "payment-api"), null);

        Map<String, AttributeValue> item = ItemCodec.toItem(event);

        assertEquals("503", item.get("fields").m().get("http.status").n());
        assertEquals(event, ItemCodec.fromItem(item, NormalizedEvent.class));
    }
}
