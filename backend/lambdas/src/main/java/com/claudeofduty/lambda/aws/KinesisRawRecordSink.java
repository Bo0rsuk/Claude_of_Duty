package com.claudeofduty.lambda.aws;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.RawRecord;
import com.claudeofduty.core.port.RawRecordSink;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;

/** Puts raw records on the stream, keyed by record id so that load spreads evenly over shards. */
public final class KinesisRawRecordSink implements RawRecordSink {

    private final KinesisClient kinesis;
    private final String streamName;

    public KinesisRawRecordSink(KinesisClient kinesis, String streamName) {
        this.kinesis = kinesis;
        this.streamName = streamName;
    }

    @Override
    public void publish(RawRecord record) {
        kinesis.putRecord(request -> request
                .streamName(streamName)
                .partitionKey(record.recordId())
                .data(SdkBytes.fromUtf8String(Json.write(record))));
    }
}
