# Contracts

Shared data shapes and interfaces. Backend, frontend and infrastructure are
built against these files.

| File | Contents |
|---|---|
| [sources-and-events.md](sources-and-events.md) | Source definition, parser configuration, raw record on Kinesis, normalized event, mapping of the five mock formats |
| [rules.md](rules.md) | Rule: conditions, severity, routing, presentation, grouping, throttling |
| [alerts.md](alerts.md) | Alert, timeline entry, notification job, deduplication and throttling behavior |
| [data-model.md](data-model.md) | DynamoDB tables and keys |
| [api.md](api.md) | REST endpoints, roles, real-time channels |

## Conventions

- JSON field names are camelCase.
- Timestamps are ISO-8601 in UTC with milliseconds, e.g. `2026-11-20T20:59:22.373Z`.
- IDs are opaque strings, generated as UUIDs, except `alertId` (see [alerts.md](alerts.md)).
- Every stored object carries `orgId`. The API takes it from the caller's membership, never from the request body.
- `level` is what the source reported: `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL`, `UNKNOWN`.
- `severity` is what a rule assigns to an alert: `CRITICAL`, `WARNING`, `INFO`.

## Latency stamps

Every stage adds one timestamp, so the load test reports measured latency per
stage and end to end.

| Stamp | Set by | Meaning |
|---|---|---|
| `receivedAt` | ingest or connector worker | Our system first saw the payload |
| `processedAt` | processor | Rules evaluated, alert written |
| `publishedAt` | processor | Sent to the real-time channel |
| `deliveredAt` | notifier | Provider accepted the SMS, call or email |
