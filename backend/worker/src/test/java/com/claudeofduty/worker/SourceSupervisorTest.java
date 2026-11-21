package com.claudeofduty.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.claudeofduty.core.model.Source;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceSupervisorTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    private final List<String> log = new ArrayList<>();
    private final ConnectorRegistry registry = new ConnectorRegistry();
    private final SourceSupervisor supervisor = new SourceSupervisor(registry, record -> { });

    SourceSupervisorTest() {
        registry.register(Source.HTTP_PULL, (source, sink) -> new Connector() {
            @Override
            public void start() {
                log.add("start " + source.sourceId() + " " + source.connector().url());
            }

            @Override
            public void close() {
                log.add("stop " + source.sourceId());
            }
        });
        registry.register("broken", (source, sink) -> {
            throw new IllegalArgumentException("bad settings");
        });
    }

    @Test
    void startsEachEnabledPullSourceOnceAndLeavesItRunning() {
        List<Source> sources = List.of(
                source("logs", Source.HTTP_PULL, "http://a/logs", true),
                source("hook", Source.WEBHOOK, null, true),
                source("off", Source.HTTP_PULL, "http://a/off", false));

        supervisor.reconcile(sources);
        supervisor.reconcile(sources);

        assertEquals(List.of("start logs http://a/logs"), log);
        assertEquals(1, supervisor.runningCount());
    }

    @Test
    void restartsASourceWhoseConnectionSettingsChanged() {
        supervisor.reconcile(List.of(source("logs", Source.HTTP_PULL, "http://a/logs", true)));
        supervisor.reconcile(List.of(source("logs", Source.HTTP_PULL, "http://b/logs", true)));

        assertEquals(List.of("start logs http://a/logs", "stop logs", "start logs http://b/logs"), log);
    }

    @Test
    void stopsSourcesThatWereDisabledOrDeleted() {
        supervisor.reconcile(List.of(
                source("logs", Source.HTTP_PULL, "http://a/logs", true),
                source("api", Source.HTTP_PULL, "http://a/api", true)));
        supervisor.reconcile(List.of(source("logs", Source.HTTP_PULL, "http://a/logs", false)));

        assertEquals(4, log.size());
        assertEquals(List.of("stop api", "stop logs"), log.subList(2, 4).stream().sorted().toList());
        assertEquals(0, supervisor.runningCount());
    }

    @Test
    void aSourceThatCannotStartDoesNotStopTheOthers() {
        supervisor.reconcile(List.of(
                source("bad", "broken", null, true),
                source("logs", Source.HTTP_PULL, "http://a/logs", true)));

        assertEquals(List.of("start logs http://a/logs"), log);
        assertEquals(1, supervisor.runningCount());
    }

    private static Source source(String id, String type, String url, boolean enabled) {
        Source.Connector connector = new Source.Connector(type, url, 5, null, null, null, null, null, null);
        return new Source(id, "org-1", id, enabled, connector, null, null, NOW, NOW);
    }
}
