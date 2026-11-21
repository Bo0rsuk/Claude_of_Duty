# Contracts

Shared data shapes and interfaces. Backend, frontend and infrastructure are
built against these files, so a change here needs agreement from the whole team.

**Status: draft for review.** Open questions are listed at the bottom.

| File | Contents |
|---|---|
| [sources-and-events.md](sources-and-events.md) | Source definition, parser configuration, raw record on Kinesis, normalized event, mapping of the five mock formats |
| [rules.md](rules.md) | Rule: conditions, severity, routing, presentation, grouping, throttling |
| [alerts.md](alerts.md) | Alert, timeline entry, notification job, deduplication and throttling behavior |
| [data-model.md](data-model.md) | DynamoDB tables and keys |
| [api.md](api.md) | REST endpoints, roles, real-time channels |

## Conventions

- **JSON field names:** camelCase.
- **Timestamps:** ISO-8601 in UTC with milliseconds, e.g. `2026-11-20T20:59:22.373Z`. This includes the latency stamps.
- **IDs:** opaque strings. Generated as UUIDs, except `alertId` (see [alerts.md](alerts.md)).
- **Tenancy:** every stored object carries `orgId`. The API takes it from the caller's membership, never from the request body.
- **Two different scales, two different names:**
  - `level` is what the source reported: `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL`, `UNKNOWN`.
  - `severity` is what a rule assigns to an alert: `CRITICAL`, `WARNING`, `INFO`.

## Latency stamps

Every stage adds one timestamp so that the load test can report measured
p50/p99 per stage and end to end.

| Stamp | Set by | Meaning |
|---|---|---|
| `receivedAt` | ingest Lambda or connector worker | Our system first saw the payload |
| `processedAt` | processor | Rules evaluated, alert written |
| `publishedAt` | processor | Sent to the real-time channel |
| `deliveredAt` | notifier | Provider accepted the SMS, call or email |

End-to-end latency for the UI is measured by the load test client as
`time the message arrived on the WebSocket - receivedAt`.

## Open questions

1. **Roles.** This draft uses `admin`, `team_lead`, `user` from `Docs/FrontPages/list-of-pages.md`, with `team_lead` being the role that manages sources and rules.
2. **Deleting alerts.** The page list mentions deleting an alert. The draft has no delete: an alert is closed with `resolve`, so its history stays intact.
3. **Reminders.** An open alert absorbs repeated events until it is resolved and never notifies again. Re-notification of unacknowledged alerts is out of scope.
