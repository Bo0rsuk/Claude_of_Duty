package com.claudeofduty.aws;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.RawRecord;
import com.claudeofduty.core.port.RawRecordSink;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;

/** Puts raw records on the stream, keyed by record id so that load spreads evenly over shards. */
public final class KinesisRawRecordSink implements RawRecordSink {

    /** The most records one PutRecords call accepts. */
    private static final int MAX_BATCH = 500;
    private static final int MAX_ATTEMPTS = 5;

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

    @Override
    public void publishAll(List<RawRecord> records) {
        for (int start = 0; start < records.size(); start += MAX_BATCH) {
            putBatch(records.subList(start, Math.min(start + MAX_BATCH, records.size())));
        }
    }

    /** PutRecords can accept some records and refuse others, so the refused ones are sent again. */
    private void putBatch(List<RawRecord> records) {
        List<PutRecordsRequestEntry> pending = records.stream()
                .map(record -> PutRecordsRequestEntry.builder()
                        .partitionKey(record.recordId())
                        .data(SdkBytes.fromUtf8String(Json.write(record)))
                        .build())
                .toList();
        for (int attempt = 1; !pending.isEmpty(); attempt++) {
            List<PutRecordsRequestEntry> sending = pending;
            PutRecordsResponse response = kinesis.putRecords(request -> request.streamName(streamName).records(sending));
            if (response.failedRecordCount() == null || response.failedRecordCount() == 0) {
                return;
            }
            if (attempt == MAX_ATTEMPTS) {
                throw new IllegalStateException(response.failedRecordCount() + " records were refused by the stream");
            }
            List<PutRecordsRequestEntry> refused = new ArrayList<>();
            for (int i = 0; i < sending.size(); i++) {
                if (response.records().get(i).errorCode() != null) {
                    refused.add(sending.get(i));
                }
            }
            pending = refused;
            pause(100L * attempt);
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying a stream write", e);
        }
    }
}
