package com.claudeofduty.worker;

import com.claudeofduty.core.model.RawRecord;
import com.claudeofduty.core.model.Source;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Wraps payloads pulled from a source into the records the pipeline expects. */
final class RawRecords {

    private RawRecords() {
    }

    static List<RawRecord> of(Source source, List<String> payloads, Instant receivedAt) {
        return payloads.stream()
                .map(payload -> new RawRecord(
                        UUID.randomUUID().toString(), source.orgId(), source.sourceId(), receivedAt, payload))
                .toList();
    }
}
