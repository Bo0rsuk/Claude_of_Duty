package com.claudeofduty.worker;

import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.port.RawRecordSink;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Keeps one running connector per enabled source. Called on a timer with the
 * current list of sources, it starts connectors for new sources, restarts
 * those whose connection settings changed and stops those that were disabled
 * or deleted. This is how a source added in the UI starts flowing without a deployment.
 */
final class SourceSupervisor {

    private record Running(Source.Connector config, Connector connector) {
    }

    private final ConnectorRegistry registry;
    private final RawRecordSink sink;
    private final Map<String, Running> running = new HashMap<>();

    SourceSupervisor(ConnectorRegistry registry, RawRecordSink sink) {
        this.registry = registry;
        this.sink = sink;
    }

    synchronized void reconcile(List<Source> sources) {
        Set<String> wanted = new HashSet<>();
        for (Source source : sources) {
            if (!source.enabled() || source.connector() == null
                    || registry.find(source.connector().type()).isEmpty()) {
                continue;
            }
            String key = source.orgId() + "/" + source.sourceId();
            wanted.add(key);
            Running current = running.get(key);
            if (current != null && Objects.equals(current.config(), source.connector())) {
                continue;
            }
            if (current != null) {
                stop(key, "its connection settings changed");
            }
            start(key, source);
        }
        for (String key : Set.copyOf(running.keySet())) {
            if (!wanted.contains(key)) {
                stop(key, "it was disabled or deleted");
            }
        }
    }

    synchronized void stopAll() {
        Set.copyOf(running.keySet()).forEach(key -> stop(key, "the worker is shutting down"));
    }

    synchronized int runningCount() {
        return running.size();
    }

    private void start(String key, Source source) {
        try {
            Connector connector = registry.find(source.connector().type()).orElseThrow().create(source, sink);
            connector.start();
            running.put(key, new Running(source.connector(), connector));
            System.out.println("Started " + source.connector().type() + " connector for " + key + " (" + source.name() + ")");
        } catch (RuntimeException e) {
            // A source with broken settings must not keep the others from running. It is tried again next time.
            System.err.println("Could not start connector for " + key + ": " + e);
        }
    }

    private void stop(String key, String reason) {
        Running stopped = running.remove(key);
        if (stopped != null) {
            stopped.connector().close();
            System.out.println("Stopped connector for " + key + " because " + reason);
        }
    }
}
