package com.claudeofduty.worker;

import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.port.RawRecordSink;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

/** Maps a source's connector type to the code that runs it. */
public final class ConnectorRegistry {

    @FunctionalInterface
    public interface Factory {
        Connector create(Source source, RawRecordSink sink);
    }

    private final Map<String, Factory> factories = new ConcurrentHashMap<>();

    /** Webhook sources are absent on purpose: they are pushed to the ingest endpoint, not pulled. */
    public static ConnectorRegistry withDefaults(ScheduledExecutorService scheduler) {
        ConnectorRegistry registry = new ConnectorRegistry();
        registry.register(Source.HTTP_PULL, (source, sink) -> new HttpPullConnector(source, sink, scheduler));
        registry.register(Source.KAFKA, KafkaConnector::new);
        return registry;
    }

    public void register(String connectorType, Factory factory) {
        factories.put(connectorType, factory);
    }

    public Optional<Factory> find(String connectorType) {
        return Optional.ofNullable(connectorType).map(factories::get);
    }
}
