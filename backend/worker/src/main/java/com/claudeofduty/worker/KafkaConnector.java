package com.claudeofduty.worker;

import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.port.RawRecordSink;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Consumes a Kafka topic. Each message value is one payload.
 *
 * Offsets are committed after the records are handed over, so a crash in
 * between redelivers them rather than losing them.
 */
final class KafkaConnector implements Connector {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration RETRY_PAUSE = Duration.ofSeconds(5);

    private final Source source;
    private final RawRecordSink sink;
    private final Clock clock = Clock.systemUTC();
    private final Thread thread;
    private volatile KafkaConsumer<String, String> consumer;
    private volatile boolean closed;

    KafkaConnector(Source source, RawRecordSink sink) {
        this.source = source;
        this.sink = sink;
        this.thread = new Thread(this::run, "kafka-" + source.sourceId());
        this.thread.setDaemon(true);
    }

    @Override
    public void start() {
        thread.start();
    }

    @Override
    public void close() {
        closed = true;
        KafkaConsumer<String, String> current = consumer;
        if (current != null) {
            current.wakeup();
        }
    }

    /** Reconnects after a failure, e.g. when the broker is restarted. */
    private void run() {
        while (!closed) {
            try (KafkaConsumer<String, String> kafka = new KafkaConsumer<>(properties())) {
                consumer = kafka;
                kafka.subscribe(List.of(source.connector().topic()));
                while (!closed) {
                    consume(kafka);
                }
            } catch (WakeupException e) {
                // close() was called.
            } catch (RuntimeException e) {
                System.err.println("Kafka source " + source.name() + " failed, reconnecting: " + e);
                pause();
            }
        }
    }

    private void consume(KafkaConsumer<String, String> kafka) {
        ConsumerRecords<String, String> records = kafka.poll(POLL_TIMEOUT);
        if (records.isEmpty()) {
            return;
        }
        List<String> payloads = new ArrayList<>(records.count());
        for (ConsumerRecord<String, String> record : records) {
            if (record.value() != null) {
                payloads.add(record.value());
            }
        }
        sink.publishAll(RawRecords.of(source, payloads, clock.instant()));
        kafka.commitSync();
    }

    private Properties properties() {
        Source.Connector config = source.connector();
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG,
                config.groupId() == null ? "claude-of-duty-" + source.sourceId() : config.groupId());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        // A source added today should alert on what happens from now on, not on the topic's history.
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return properties;
    }

    private void pause() {
        try {
            Thread.sleep(RETRY_PAUSE.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closed = true;
        }
    }
}
