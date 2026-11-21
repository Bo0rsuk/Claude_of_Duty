# Rules

A rule belongs to one source and decides which of its events become alerts, how
severe they are, who is notified, how the alert looks and how repeats are
handled. Rules are DynamoDB items. The processor re-reads them every 15 seconds
at most, so a change made in the UI takes effect without a restart.

```json
{
  "ruleId": "c41a...",
  "orgId": "org-1",
  "sourceId": "0b7f3c1e-...",
  "name": "Payment database timeouts",
  "enabled": true,

  "match": {
    "mode": "all",
    "conditions": [
      { "field": "level", "op": "in", "value": ["ERROR", "FATAL"] },
      { "field": "code", "op": "eq", "value": "DB_TIMEOUT" },
      { "field": "http.latency_ms", "op": "gt", "value": 1000 }
    ]
  },

  "severity": "CRITICAL",

  "routing": [
    { "channel": "sms",   "target": { "type": "group", "id": "grp-cloud-engs" } },
    { "channel": "voice", "target": { "type": "user", "id": "usr-1" } },
    { "channel": "email", "target": { "type": "address", "value": "oncall@example.pl" } },
    { "channel": "ui",    "target": { "type": "group", "id": "grp-cloud-engs" } }
  ],

  "presentation": {
    "title": "{{service}}: {{code}}",
    "body": "{{message}} on {{host}}",
    "fields": ["service", "host", "http.status", "http.latency_ms"],
    "channels": {
      "sms": { "body": "[{{severity}}] {{service}} {{code}} x{{count}}" },
      "email": { "subject": "[{{severity}}] {{ruleName}}", "body": "{{message}}\nHost: {{host}}" }
    }
  },

  "groupBy": ["service", "code"],

  "throttle": {
    "perRule": { "max": 5, "windowSeconds": 300 },
    "perRecipient": { "max": 2, "windowSeconds": 300 }
  },

  "delivery": {
    "window": { "from": "08:00", "to": "20:00", "timezone": "Europe/Warsaw" },
    "outsideWindow": "email"
  },

  "runbookId": "rb-db-timeout",

  "createdAt": "2026-11-20T21:00:00.000Z",
  "updatedAt": "2026-11-20T21:05:00.000Z",
  "updatedBy": "usr-1"
}
```

## match

`mode` is `all` (every condition must hold) or `any` (at least one).
An empty `conditions` list matches every event of the source.

`field` is `level`, `message`, or a key of the event's `fields`.

| `op` | Holds when | `value` |
|---|---|---|
| `eq` / `neq` | field equals / does not equal the value | string, number or boolean |
| `in` | field equals one of the values | array |
| `contains` | field contains the value as a substring | string |
| `regex` | the Java regular expression finds a match in the field | string |
| `gt` `gte` `lt` `lte` | numeric comparison | number |
| `exists` | field is present; `value: false` inverts it | boolean, optional |

Evaluation rules:

- Equality compares text forms, so `"503"` equals `503`. This keeps regex-parsed sources and JSON sources interchangeable.
- Numeric operators convert both sides to numbers. A field that is not a number does not match.
- String comparison is case-sensitive.
- A missing field fails every operator except `neq` and `exists: false`.

A new operator is one class implementing the operator interface, registered by name.

## severity and routing

`severity` is `CRITICAL`, `WARNING` or `INFO`. It only labels the alert. Who
gets notified is decided by `routing` alone, so any combination is possible.

The UI pre-fills `routing` when the severity is picked:

| Severity | Default channels |
|---|---|
| `CRITICAL` | `sms`, `voice`, `push` |
| `WARNING` | `email`, `push` |
| `INFO` | `ui` |

| `channel` | Delivers | Valid targets |
|---|---|---|
| `sms` | Text message with a link to the alert | `group`, `user`, `address` (phone number) |
| `voice` | Phone call reading the alert title | `group`, `user`, `address` (phone number) |
| `email` | Email with the alert fields and a link | `group`, `user`, `address` (email) |
| `ui` | Pop-up in the application with the alert window | `group`, `user` |
| `push` | System notification on every device where the user enabled push, also when the application is closed | `group`, `user` |

Every alert appears in the organization's live feed regardless of `routing`.
The `ui` channel adds a pop-up for the targeted people.

A `group` target is expanded to its members when the notification is sent, so
changing a group changes who is notified without touching rules.

A new channel is one class implementing `NotificationChannel`, plus its queue.

## presentation

Templates use `{{name}}` placeholders. Unknown names render as an empty string.

| Placeholder | Value |
|---|---|
| `{{level}}`, `{{message}}` | From the first event of the alert |
| `{{<field>}}` | Any key of the event's `fields`, e.g. `{{http.status}}` |
| `{{severity}}`, `{{ruleName}}`, `{{sourceName}}` | From the rule and source |
| `{{count}}` | Number of events grouped into the alert so far |
| `{{alertUrl}}` | Link to the alert window in the application |

- `title` and `body` are used in the live feed, the alert window and as the default for every channel.
- `fields` lists the event fields shown as a table in the alert window and, as `name: value` lines, in emails without their own template.
- `channels` optionally overrides the text per channel. SMS and email always end with `{{alertUrl}}`, even when the template omits it.

## groupBy

Events matched by the rule are grouped into one alert when they share the same
values for these fields. An empty list groups everything the rule matches into
a single alert. See [alerts.md](alerts.md) for the mechanics.

## throttle

Both parts are optional.

- `perRule`: at most `max` notifications from this rule per window, counted over all its alerts.
- `perRecipient`: at most `max` notifications from this rule to the same recipient on the same channel per window.

A suppressed notification is recorded in the alert timeline. The alert itself
is always created and shown in the live feed.

## delivery

Optional. Without it the routing applies at any hour.

- `window.from` and `window.to` are times of day as `HH:mm`, read in `window.timezone` (an IANA name such as `Europe/Warsaw`; UTC when absent). `from` is inside the window, `to` is not. A window whose `to` is earlier than its `from` runs over midnight, e.g. `22:00` to `06:00`.
- Inside the window `routing` is used as it is.
- Outside the window everyone the routing names is notified once on the `outsideWindow` channel instead, if they have the address that channel needs. A bare `address` target stays only when the channel delivers to that kind of address. Throttling applies as usual.
- With `outsideWindow` null, nothing is sent outside the window. Each notification held back is recorded in the timeline as `NOTIFICATION_SUPPRESSED` with the reason `deliveryWindow`.
- The alert itself is always created and shown in the live feed, and a resend that a person asks for goes out on the rule's own channels at any hour.

## runbookId

Optional reference to a runbook from the library. The alert window shows the
runbook; SMS and email link to that window.

```json
{ "runbookId": "rb-db-timeout", "orgId": "org-1", "title": "Database timeouts",
  "format": "checklist", "items": ["Check connection pool metrics", "Fail over to the replica"],
  "createdAt": "2026-11-20T21:00:00.000Z", "updatedAt": "2026-11-20T21:00:00.000Z" }
```

| `format` | Content field |
|---|---|
| `markdown` | `content`: Markdown text |
| `text` | `content`: plain text |
| `link` | `url`: link to a wiki or Confluence page |
| `checklist` | `items`: array of steps |
