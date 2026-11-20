package com.claudeofduty.core.port;

import com.claudeofduty.core.model.RawRecord;

/** Where connectors hand over payloads for processing. */
public interface RawRecordSink {

    void publish(RawRecord record);
}
