package com.claudeofduty.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.claudeofduty.core.model.RawRecord;
import com.claudeofduty.core.model.Source;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Polls a local server that answers like the mock data source: JSON with a body cursor, text with a header cursor. */
class HttpPullConnectorTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    private final List<String> requestedQueries = new ArrayList<>();
    private final List<RawRecord> published = new ArrayList<>();
    private HttpServer server;
    private int status = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/events", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            requestedQueries.add(String.valueOf(query));
            // Without a cursor: the two newest items. With after=2: one more. Then nothing new.
            String body = query == null
                    ? "{\"items\":[{\"seq\":1,\"level\":\"INFO\"},{\"seq\":2,\"level\":\"ERROR\"}],\"next_after\":2}"
                    : query.equals("after=2")
                            ? "{\"items\":[{\"seq\":3,\"level\":\"WARN\"}],\"next_after\":3}"
                            : "{\"items\":[],\"next_after\":3}";
            respond(exchange, body, null);
        });
        server.createContext("/logs", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            requestedQueries.add(String.valueOf(query));
            respond(exchange, query == null ? "first line\n\nsecond line\n" : "third line\n", query == null ? "20" : "21");
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void jsonItemsBecomeRecordsAndTheCursorFromTheBodyIsSentBack() throws Exception {
        HttpPullConnector connector = connector("/api/events", "items", "body:next_after");

        connector.poll();
        connector.poll();
        connector.poll();

        assertEquals(List.of("null", "after=2", "after=3"), requestedQueries);
        assertEquals(List.of("{\"seq\":1,\"level\":\"INFO\"}", "{\"seq\":2,\"level\":\"ERROR\"}", "{\"seq\":3,\"level\":\"WARN\"}"),
                published.stream().map(RawRecord::payload).toList());
        RawRecord first = published.get(0);
        assertEquals("org-1", first.orgId());
        assertEquals("src-1", first.sourceId());
        assertEquals(NOW, first.receivedAt());
        assertEquals(3, published.stream().map(RawRecord::recordId).distinct().count());
    }

    @Test
    void textLinesBecomeRecordsAndTheCursorComesFromAHeader() throws Exception {
        HttpPullConnector connector = connector("/logs", null, "header:X-Next-After");

        connector.poll();
        connector.poll();

        assertEquals(List.of("null", "after=20"), requestedQueries);
        assertEquals(List.of("first line", "second line", "third line"),
                published.stream().map(RawRecord::payload).toList());
    }

    @Test
    void failedRequestIsReportedAndTheCursorStaysWhereItWas() throws Exception {
        HttpPullConnector connector = connector("/api/events", "items", "body:next_after");
        connector.poll();

        status = 503;
        assertThrows(IllegalStateException.class, connector::poll);
        status = 200;
        connector.poll();

        assertEquals(List.of("null", "after=2", "after=2"), requestedQueries);
        assertEquals(3, published.size());
    }

    @Test
    void cursorDoesNotMoveWhenTheRecordsCouldNotBeHandedOver() throws Exception {
        List<String> queries = requestedQueries;
        HttpPullConnector connector = new HttpPullConnector(source("/api/events", "items", "body:next_after"),
                record -> {
                    throw new IllegalStateException("stream unavailable");
                }, null, HttpClient.newHttpClient(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(IllegalStateException.class, connector::poll);
        assertThrows(IllegalStateException.class, connector::poll);

        assertEquals(List.of("null", "null"), queries);
        assertTrue(published.isEmpty());
    }

    private HttpPullConnector connector(String path, String itemsPath, String cursorFrom) {
        return new HttpPullConnector(source(path, itemsPath, cursorFrom), published::add, null,
                HttpClient.newHttpClient(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Source source(String path, String itemsPath, String cursorFrom) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + path;
        Source.Connector connector =
                new Source.Connector(Source.HTTP_PULL, url, 5, itemsPath, "after", cursorFrom, null, null, null);
        return new Source("src-1", "org-1", "Mock", true, connector, null, null, NOW, NOW);
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, String body, String cursorHeader) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (cursorHeader != null) {
            exchange.getResponseHeaders().add("X-Next-After", cursorHeader);
        }
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
