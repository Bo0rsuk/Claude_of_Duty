# Sources and events

A source is a connector plus a parser configuration, stored as one DynamoDB
item. Adding a source is a write to that table, not a deployment.

```
connector (webhook | http-pull | kafka)  ->  RawRecord on Kinesis
  ->  processor applies the source's parser  ->  NormalizedEvent(s)  ->  rules
```

## Source

```json
{
  "sourceId": "0b7f3c1e-...",
  "orgId": "org-1",
  "name": "Payments API",
  "enabled": true,
  "connector": { "type": "http-pull", "url": "https://datasource.waw-out.pl/api/events", "intervalSeconds": 5,
                 "itemsPath": "items", "cursorParam": "after", "cursorFrom": "body:next_after" },
  "parser": { "format": "json", "eventTime": "timestamp", "timeFormat": "iso8601",
              "level": "level", "message": "message" },
  "ingestToken": "random string, webhook sources only",
  "createdAt": "2026-11-20T21:00:00.000Z",
  "updatedAt": "2026-11-20T21:00:00.000Z"
}
```

### Connector types

| `type` | Fields | Behavior |
|---|---|---|
| `webhook` | none | The sender posts to `POST /ingest/{orgId}/{sourceId}?token={ingestToken}`. The whole body is one raw record. |
| `http-pull` | `url`, `intervalSeconds`, `cursorParam`, `cursorFrom`, `itemsPath` | The worker calls `url?{cursorParam}={cursor}` every interval. `cursorFrom` is `body:<json path>` or `header:<name>`. With `itemsPath` set, each element of that JSON array is one raw record. Without it the body is text and each line is one raw record. |
| `kafka` | `bootstrapServers`, `topic`, `groupId` | The worker consumes the topic. Each message value is one raw record. |

A new connector type is one class implementing the connector interface in the worker.

### Parser

| Field | Applies to | Meaning |
|---|---|---|
| `format` | all | `json` or `regex` |
| `pattern` | `regex` | Java regular expression with named groups. Each group becomes a field. Group names allow letters and digits only, so use `traceId`, not `trace_id`. |
| `split` | `json` | Path to an array. Each element becomes its own event, with the parent's other fields copied in. |
| `eventTime` | all | Path (json) or group name (regex) holding the event time. Missing or unparsable: `receivedAt` is used. |
| `timeFormat` | all | `iso8601`, `epoch_millis`, or a Java `DateTimeFormatter` pattern (English locale, UTC when the value has no offset) |
| `level` | all | Path or group name holding the level. Missing: `UNKNOWN`. |
| `message` | all | Path or group name holding the message. Missing: the raw payload. |

Level values are upper-cased and normalized: `WARNING` becomes `WARN`, `ERR`
becomes `ERROR`, `CRITICAL` becomes `FATAL`, anything unrecognized becomes `UNKNOWN`.

A new parser format is one class implementing `Parser`.

## RawRecord

What connectors put on Kinesis. The partition key is `recordId`, so load
spreads evenly over shards. Nothing downstream depends on ordering.

```json
{
  "recordId": "5c1d...",
  "orgId": "org-1",
  "sourceId": "0b7f3c1e-...",
  "receivedAt": "2026-11-20T20:59:22.401Z",
  "payload": "the original payload as a string"
}
```

## NormalizedEvent

```json
{
  "eventId": "9a2e...",
  "orgId": "org-1",
  "sourceId": "0b7f3c1e-...",
  "eventTime": "2026-11-20T20:59:22.373Z",
  "receivedAt": "2026-11-20T20:59:22.401Z",
  "level": "ERROR",
  "message": "Database connection timeout after 3000 ms",
  "fields": {
    "service": "payment-api",
    "host": "payment-api-1",
    "code": "DB_TIMEOUT",
    "http.status": 503,
    "http.latency_ms": 3012
  },
  "raw": "the original payload as a string"
}
```

Rules for `fields`:

- Flat map. Nested JSON objects are flattened with dots: `http.status`, `labels.service`.
- JSON values keep their type (string, number, boolean). Arrays are stored as their JSON text. Regex captures are strings.
- The keys used for `eventTime`, `level` and `message` are not repeated in `fields`.
- `level` and `message` are reserved names. Conditions and templates address them directly, everything else through `fields`.

## The five mock formats

Parser configurations for the sources served by `mocks/datasource`. The UI
offers these as presets. Regexes and time formats below were checked in Java
against real lines from the mock.

### JSON API - `GET /api/events`

```json
{ "connector": { "type": "http-pull", "url": "https://datasource.waw-out.pl/api/events", "intervalSeconds": 5,
                 "itemsPath": "items", "cursorParam": "after", "cursorFrom": "body:next_after" },
  "parser": { "format": "json", "eventTime": "timestamp", "timeFormat": "iso8601", "level": "level", "message": "message" } }
```

Fields: `seq`, `id`, `service`, `host`, `code`, `http.method`, `http.path`, `http.status`, `http.latency_ms`, `trace_id`.

### Application log - `GET /logs`

```json
{ "connector": { "type": "http-pull", "url": "https://datasource.waw-out.pl/logs", "intervalSeconds": 5,
                 "cursorParam": "after", "cursorFrom": "header:X-Next-After" },
  "parser": { "format": "regex", "eventTime": "timestamp", "timeFormat": "yyyy-MM-dd HH:mm:ss,SSS", "level": "level", "message": "message",
              "pattern": "^(?<timestamp>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3}) (?<level>\\w+)\\s+\\[(?<service>[^\\]]+)\\] \\[(?<host>[^\\]]+)\\] \\[trace=(?<traceId>[^\\]]+)\\] (?<code>\\S+) - (?<message>.*)$" } }
```

Fields: `service`, `host`, `traceId`, `code`.

### Access log - `GET /logs/access`

```json
{ "connector": { "type": "http-pull", "url": "https://datasource.waw-out.pl/logs/access", "intervalSeconds": 5,
                 "cursorParam": "after", "cursorFrom": "header:X-Next-After" },
  "parser": { "format": "regex", "eventTime": "timestamp", "timeFormat": "dd/MMM/yyyy:HH:mm:ss Z",
              "pattern": "^(?<clientIp>\\S+) \\S+ \\S+ \\[(?<timestamp>[^\\]]+)\\] \"(?<method>\\S+) (?<path>\\S+) [^\"]*\" (?<status>\\d{3}) (?<bytes>\\d+) \"[^\"]*\" \"(?<userAgent>[^\"]*)\" (?<requestTime>[\\d.]+)$" } }
```

Fields: `clientIp`, `method`, `path`, `status`, `bytes`, `userAgent`, `requestTime`.
This format has no level, so `level` is `UNKNOWN` and `message` is the raw line.
Rules for it filter on `status` or `requestTime`.

### Kafka - topic `infra.events`

```json
{ "connector": { "type": "kafka", "bootstrapServers": "datasource.waw-out.pl:9092", "topic": "infra.events", "groupId": "claude-of-duty" },
  "parser": { "format": "json", "eventTime": "ts", "timeFormat": "epoch_millis", "level": "severity", "message": "summary" } }
```

Fields: `source`, `kind`, `labels.service`, `labels.pod`, `labels.namespace`, `value`.

### Webhook - Alertmanager style

```json
{ "connector": { "type": "webhook" },
  "parser": { "format": "json", "split": "alerts", "eventTime": "startsAt", "timeFormat": "iso8601",
              "level": "labels.severity", "message": "annotations.summary" } }
```

Fields: `version`, `receiver`, `status`, `labels.alertname`, `labels.service`, `labels.instance`.

## Custom source

The user picks a connector, pastes a sample payload and fills in the parser
fields above. `POST /sources/preview` runs the parser on the sample and returns
the resulting events, so the mapping is visible before the source is saved.
