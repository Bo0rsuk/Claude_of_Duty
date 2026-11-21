package com.claudeofduty.worker;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.port.RawRecordSink;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Polls an HTTP endpoint on an interval and remembers how far it has read.
 *
 * The cursor is kept in memory. After a restart the endpoint is read from its
 * newest items again, and deduplication absorbs what was seen before.
 */
final class HttpPullConnector implements Connector {

    private static final int DEFAULT_INTERVAL_SECONDS = 5;
    private static final String BODY_PREFIX = "body:";
    private static final String HEADER_PREFIX = "header:";

    private final Source source;
    private final Source.Connector config;
    private final RawRecordSink sink;
    private final ScheduledExecutorService scheduler;
    private final HttpClient http;
    private final Clock clock;
    private ScheduledFuture<?> schedule;
    private String cursor;

    HttpPullConnector(Source source, RawRecordSink sink, ScheduledExecutorService scheduler) {
        this(source, sink, scheduler, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                Clock.systemUTC());
    }

    HttpPullConnector(Source source, RawRecordSink sink, ScheduledExecutorService scheduler, HttpClient http, Clock clock) {
        this.source = source;
        this.config = source.connector();
        this.sink = sink;
        this.scheduler = scheduler;
        this.http = http;
        this.clock = clock;
    }

    @Override
    public void start() {
        int interval = config.intervalSeconds() == null ? DEFAULT_INTERVAL_SECONDS : Math.max(1, config.intervalSeconds());
        schedule = scheduler.scheduleWithFixedDelay(this::pollSafely, 0, interval, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        if (schedule != null) {
            schedule.cancel(false);
        }
    }

    /** A failed poll is logged and tried again on the next tick; it must not end the schedule. */
    private void pollSafely() {
        try {
            poll();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("Poll of " + source.name() + " (" + config.url() + ") failed: " + e);
        }
    }

    /** One request: read what is new, hand it over, then move the cursor. */
    void poll() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(uri()).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        JsonNode json = config.itemsPath() == null ? null : Json.tree(response.body());
        List<String> payloads = json == null ? lines(response.body()) : items(json, config.itemsPath());

        // The cursor moves only after the records are handed over, so a failed write is read again.
        sink.publishAll(RawRecords.of(source, payloads, clock.instant()));
        String next = nextCursor(response, json);
        if (next != null) {
            cursor = next;
        }
    }

    private URI uri() {
        if (cursor == null || config.cursorParam() == null) {
            return URI.create(config.url());
        }
        String separator = config.url().contains("?") ? "&" : "?";
        return URI.create(config.url() + separator + config.cursorParam() + "="
                + URLEncoder.encode(cursor, StandardCharsets.UTF_8));
    }

    private String nextCursor(HttpResponse<String> response, JsonNode json) {
        String from = config.cursorFrom();
        if (from == null) {
            return null;
        }
        if (from.startsWith(HEADER_PREFIX)) {
            return response.headers().firstValue(from.substring(HEADER_PREFIX.length())).orElse(null);
        }
        if (from.startsWith(BODY_PREFIX)) {
            JsonNode value = at(json != null ? json : Json.tree(response.body()), from.substring(BODY_PREFIX.length()));
            return value.isMissingNode() || value.isNull() ? null : value.asText();
        }
        return null;
    }

    /** Each element of the JSON array is one payload. */
    private static List<String> items(JsonNode json, String path) {
        List<String> payloads = new ArrayList<>();
        for (JsonNode item : at(json, path)) {
            payloads.add(item.toString());
        }
        return payloads;
    }

    /** Each non-empty line of a text body is one payload. */
    private static List<String> lines(String body) {
        return body.lines().filter(line -> !line.isBlank()).toList();
    }

    private static JsonNode at(JsonNode root, String path) {
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            current = current.path(part);
        }
        return current;
    }
}
