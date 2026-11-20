# API

## REST

HTTP API on API Gateway. Every endpoint except `/ingest` requires a Cognito
JWT in `Authorization: Bearer <token>`. The organization is taken from the
caller's membership.

Conventions:

- Lists return `{ "items": [...], "nextCursor": "..." }`. Pass `cursor` to get the next page. `nextCursor` is absent on the last page.
- Errors return `{ "error": { "code": "NOT_FOUND", "message": "Rule c41a does not exist" } }` with a matching HTTP status. Codes: `VALIDATION_FAILED` (400), `UNAUTHENTICATED` (401), `FORBIDDEN` (403), `NOT_FOUND` (404), `CONFLICT` (409).
- `POST` that creates returns 201 with the created object. `PUT` replaces the whole object. `DELETE` returns 204.

### Roles

| Role | Can do |
|---|---|
| `user` | Read everything, act on alerts (acknowledge, resolve, assign, add notes), edit own profile |
| `team_lead` | Everything a `user` can, plus create, change and delete sources, rules, groups and runbooks |
| `admin` | Everything a `team_lead` can, plus manage members, roles and the invite code |

The minimum role is given in the last column of each table.

### Account and organization

| Method and path | Purpose | Role |
|---|---|---|
| `GET /me` | Profile and membership: `{ userId, email, name, phone, org: { orgId, name, role } }`. `org` is null before joining. | any |
| `PATCH /me` | Update `name` and `phone` | any |
| `POST /orgs` | Create an organization `{ name }`. The caller becomes its `admin`. | any, no org yet |
| `POST /orgs/join` | Join with `{ inviteCode }` as `user` | any, no org yet |
| `GET /org` | Organization details. `inviteCode` is included for `admin` only. | user |
| `POST /org/invite-code` | Generate a new invite code | admin |
| `GET /members` | List members | user |
| `PATCH /members/{userId}` | Change `{ role }` | admin |
| `DELETE /members/{userId}` | Remove a member | admin |

### Sources

| Method and path | Purpose | Role |
|---|---|---|
| `GET /source-presets` | Known source types with pre-filled `connector` and `parser` | user |
| `GET /sources` | List sources | user |
| `POST /sources` | Create a source | team_lead |
| `GET /sources/{sourceId}` | Read a source | user |
| `PUT /sources/{sourceId}` | Replace a source | team_lead |
| `DELETE /sources/{sourceId}` | Delete a source and its rules | team_lead |
| `POST /sources/preview` | Run `{ parser, sample }` and return `{ events: [NormalizedEvent], errors: [...] }` without saving | team_lead |
| `GET /sources/{sourceId}/events?limit=100` | Recent events of the source, newest first | user |

### Rules

| Method and path | Purpose | Role |
|---|---|---|
| `GET /rules?sourceId=` | List rules, optionally for one source | user |
| `POST /rules` | Create a rule | team_lead |
| `GET /rules/{ruleId}` | Read a rule | user |
| `PUT /rules/{ruleId}` | Replace a rule | team_lead |
| `DELETE /rules/{ruleId}` | Delete a rule | team_lead |
| `POST /rules/preview` | Evaluate `{ sourceId, match }` against recent events: `{ matched: 7, total: 100, samples: [NormalizedEvent] }` | team_lead |
| `POST /ai/rule-suggestion` | Turn `{ sourceId, sampleEvent, goal }` into `{ match, explanation }`. `goal` is the user's description in plain language. | team_lead |

### Alerts

| Method and path | Purpose | Role |
|---|---|---|
| `GET /alerts?status=&severity=&ruleId=&limit=` | List alerts, newest first | user |
| `GET /alerts/{alertId}` | Alert with the `runbook` object embedded | user |
| `GET /alerts/{alertId}/timeline` | Timeline entries, oldest first | user |
| `POST /alerts/{alertId}/ack` | Acknowledge | user |
| `POST /alerts/{alertId}/resolve` | Resolve | user |
| `POST /alerts/{alertId}/assign` | Pass to `{ userId }` | user |
| `POST /alerts/{alertId}/notes` | Add a comment `{ text }` | user |
| `POST /alerts/{alertId}/resend` | Send the notifications again | team_lead |

The action endpoints return the updated alert and publish `alert.updated`.

### Groups and runbooks

| Method and path | Purpose | Role |
|---|---|---|
| `GET /groups`, `GET /groups/{groupId}` | Read recipient groups | user |
| `POST /groups`, `PUT /groups/{groupId}`, `DELETE /groups/{groupId}` | Manage recipient groups | team_lead |
| `GET /runbooks`, `GET /runbooks/{runbookId}` | Read runbooks | user |
| `POST /runbooks`, `PUT /runbooks/{runbookId}`, `DELETE /runbooks/{runbookId}` | Manage runbooks | team_lead |

### Ingest

| Method and path | Purpose |
|---|---|
| `POST /ingest/{orgId}/{sourceId}?token={ingestToken}` | Push one payload from a webhook source |

- No JWT. The token may also be sent as the `X-Ingest-Token` header.
- The body is stored untouched as one `RawRecord`, whatever its content type.
- Returns `202 { "recordId": "..." }`. An unknown or disabled source or a wrong token returns 404.

## Real-time channels

AppSync Events. Browsers subscribe over WebSocket with their Cognito token.
Lambdas publish over HTTP with IAM.

| Channel | Published by | Message |
|---|---|---|
| `/alerts/{orgId}` | processor, API | Alert created or changed. Drives the live feed and open alert windows. |
| `/notifications/{orgId}/{userId}` | notifier (`ui` channel) | Pop-up for one user |

Alert message:

```json
{
  "type": "alert.created",
  "alert": { "...": "Alert without sampleEvent" },
  "publishedAt": "2026-11-20T20:59:22.530Z"
}
```

`type` is `alert.created` or `alert.updated`. An update carries the whole alert
again, so the client replaces its copy. An open alert window reloads its
timeline when an update for its alert arrives.

Notification message:

```json
{ "type": "notification", "alertId": "1795208362373-9f3a61c2", "severity": "INFO",
  "title": "payment-api: DB_TIMEOUT", "body": "Database connection timeout after 3000 ms on payment-api-1",
  "publishedAt": "2026-11-20T20:59:23.204Z" }
```

A subscriber may only join the channels of its own organization. How this is
enforced in AppSync Events is still to be confirmed during the infrastructure
setup.
