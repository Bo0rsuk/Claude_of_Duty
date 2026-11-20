# Alerts

## Alert

```json
{
  "alertId": "1795208362373-9f3a61c2",
  "orgId": "org-1",
  "ruleId": "c41a...",
  "ruleName": "Payment database timeouts",
  "sourceId": "0b7f3c1e-...",
  "fingerprint": "sha256 hex",
  "severity": "CRITICAL",
  "status": "OPEN",
  "title": "payment-api: DB_TIMEOUT",
  "body": "Database connection timeout after 3000 ms on payment-api-1",
  "fields": { "service": "payment-api", "host": "payment-api-1", "http.status": 503 },
  "count": 500,
  "firstSeenAt": "2026-11-20T20:59:22.373Z",
  "lastSeenAt": "2026-11-20T20:59:41.118Z",
  "sampleEvent": { "...": "the first NormalizedEvent" },
  "runbookId": "rb-db-timeout",
  "assigneeId": null,
  "acknowledgedBy": null,
  "acknowledgedAt": null,
  "resolvedBy": null,
  "resolvedAt": null,
  "timing": {
    "receivedAt": "2026-11-20T20:59:22.401Z",
    "processedAt": "2026-11-20T20:59:22.512Z"
  }
}
```

- `alertId` is `<13-digit epoch millis of creation>-<8 hex chars>`. It sorts by creation time, so the newest alerts are read without an extra index.
- `title`, `body` and `fields` are rendered from the rule's `presentation` using the first event. `fields` holds the values of `presentation.fields`.
- `timing` belongs to the first event and feeds the latency measurements. `publishedAt` is not stored: it travels in the real-time message.
- `GET /alerts/{alertId}` returns the alert with the full `runbook` object embedded.

### Status

```
OPEN  --ack-->  ACKNOWLEDGED  --resolve-->  RESOLVED
  \------------------resolve------------------/
```

`OPEN` and `ACKNOWLEDGED` alerts are active: they absorb further matching
events. A `RESOLVED` alert is final. The next matching event opens a new alert.

## Deduplication

```
fingerprint = sha256(orgId + ruleId + values of the rule's groupBy fields)
```

1. The processor writes a pointer `fingerprint -> alertId` with a condition that no pointer exists.
2. The write succeeds: this is a new alert. Store it, add the `CREATED` timeline entry, enqueue notifications, publish `alert.created`.
3. The write fails: an active alert exists. Add the number of matching events to its `count`, update `lastSeenAt`, publish `alert.updated`. No notification is sent.
4. Resolving an alert deletes the pointer.

Within one Kinesis batch the processor aggregates by fingerprint first, so 500
identical events in a batch cost one conditional write and one counter update,
not 500.

## Throttling

Fixed windows backed by counters with a TTL. Before enqueueing a notification:

- `perRule`: increment the counter `rule#<ruleId>#<window start>` if it is below `max`.
- `perRecipient`: increment the counter `rcpt#<ruleId>#<channel>#<address>#<window start>` if it is below `max`.

If either increment is refused, the notification is not sent and a
`NOTIFICATION_SUPPRESSED` timeline entry is written instead.

## Timeline entry

The history shown in the alert window, oldest first.

```json
{
  "alertId": "1795208362373-9f3a61c2",
  "entryId": "2026-11-20T20:59:22.512Z#4be1",
  "at": "2026-11-20T20:59:22.512Z",
  "type": "NOTIFICATION_SENT",
  "actor": { "type": "system" },
  "detail": { "channel": "sms", "recipient": "+48600100200", "attempt": 1, "deliveredAt": "2026-11-20T20:59:23.204Z" }
}
```

`actor` is `{ "type": "system" }` or `{ "type": "user", "id": "usr-1", "name": "Anna Nowak" }`.

| `type` | Written when | `detail` |
|---|---|---|
| `CREATED` | The alert is created | `ruleName`, `severity` |
| `NOTIFICATION_SENT` | A provider accepted the notification | `channel`, `recipient`, `attempt`, `deliveredAt` |
| `NOTIFICATION_FAILED` | A delivery attempt failed | `channel`, `recipient`, `attempt`, `error`, `final` (true when no retries remain) |
| `NOTIFICATION_SUPPRESSED` | Throttling blocked a notification | `channel`, `recipient`, `reason` (`perRule` or `perRecipient`) |
| `ACKNOWLEDGED` | A user acknowledged the alert | none |
| `ASSIGNED` | The alert was passed to a user | `assigneeId`, `assigneeName` |
| `RESOLVED` | A user resolved the alert | none |
| `NOTE` | A user added a comment | `text` |

## Notification job

One SQS message per channel and recipient, so each delivery succeeds, fails and
retries on its own. There is one queue per channel, each with a dead-letter queue.

```json
{
  "notificationId": "7d02...",
  "orgId": "org-1",
  "alertId": "1795208362373-9f3a61c2",
  "channel": "sms",
  "recipient": { "address": "+48600100200", "userId": "usr-1", "name": "Anna Nowak" },
  "subject": null,
  "body": "[CRITICAL] payment-api DB_TIMEOUT x1 https://app.waw-out.pl/alerts/1795208362373-9f3a61c2",
  "attempt": 1,
  "receivedAt": "2026-11-20T20:59:22.401Z"
}
```

- `recipient.address` is a phone number in E.164 format, an email address, or the `userId` for the `ui` channel.
- `subject` is used by `email` only.
- `receivedAt` is carried along so the notifier can report ingest-to-delivery latency.

Delivery and retries:

1. The notifier takes the job and calls the provider. Success writes `NOTIFICATION_SENT`.
2. Failure writes `NOTIFICATION_FAILED` and leaves the message on the queue. SQS redelivers it up to 3 times.
3. After that the message moves to the dead-letter queue. The retry Lambda re-enqueues it with a delay and `attempt` increased, up to 3 more rounds.
4. When those are used up, `NOTIFICATION_FAILED` is written with `final: true`.
