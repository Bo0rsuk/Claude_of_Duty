package com.claudeofduty.core.port;

import com.claudeofduty.core.model.RawRecord;
import java.util.List;

/** Where connectors hand over payloads for processing. */
public interface RawRecordSink {

    void publish(RawRecord record);

    /** Hands over many records at once. Implementations may send them in fewer calls. */
    default void publishAll(List<RawRecord> records) {
        records.forEach(this::publish);
    }
}
