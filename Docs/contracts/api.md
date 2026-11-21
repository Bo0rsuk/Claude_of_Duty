# API

## REST

**Implemented:** everything below.

HTTP API on API Gateway. Every endpoint except `/ingest` requires a Cognito
JWT in `Authorization: Bearer <token>`. The organization is taken from the
caller's membership.

Conventions:

- Lists return `{ "items": [...], "nextCursor": "..." }`. Pass `cursor` to get the next page. `nextCursor` is absent on the last page.
- Errors return `{ "error": { "code": "NOT_FOUND", "message": "Rule c41a does not exist" } }` with a matching HTTP status. Codes: `VALIDATION_FAILED` (400), `UNAUTHENTICATED` (401), `FORBIDDEN` (403), `NOT_FOUND` (404), `CONFLICT` (409), `AI_UNAVAILABLE` and `AI_INVALID_ANSWER` (502, rule assistant only).
- `POST` that creates returns 201 with the created object. `PUT` replaces the whole object. `DELETE` returns 204.
- The server sets the id, `orgId`, `createdAt`, `updatedAt` and `updatedBy`. Values sent for these fields are ignored.
- Send the Cognito **ID token**, not the access token: the API reads the user's email from it.

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
| `GET /me` | Profile and membership: `{ userId, email, name, phone, org: { orgId, name, role }, tokenRefreshNeeded }`. `org` is null before joining. `tokenRefreshNeeded` is true when the caller's token does not carry their organization yet; the client must get a new token before subscribing to real-time channels. | any |
| `PATCH /me` | Update `name`, `phone` and `email`. The email is also the sign-in name, so it changes there too and the user signs in with the new address. `409` when the address is taken. | any |
| `POST /orgs` | Create an organization `{ name }`. The caller becomes its `admin`. | any, no org yet |
| `POST /orgs/join` | Join with `{ inviteCode }` as `user`. Whoever joins an organization that has no members yet becomes its `admin`. | any, no org yet |
| `GET /org` | Organization details. `inviteCode` is included for `admin` only. | user |
| `POST /org/invite-code` | Generate a new invite code | admin |
| `GET /members` | List members | user |
| `POST /members` | Add a person with `{ email, name?, role? }` (role defaults to `user`). Their account is created in this organization and they get a temporary password by email, which they replace on first sign-in. `409` when the email already has an account: share the invite code instead. | admin |
| `PATCH /members/{userId}` | Change any of `{ role, name, phone, email }`. A part that is left out stays as it is; an empty `phone` removes the number. A new `email` also becomes the member's sign-in name. | admin |
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
| `POST /sources/preview` | Run `{ parser, sample }` and return `{ events: [NormalizedEvent] }` without saving. A text sample is parsed line by line. A line the parser cannot read comes back as an event with no fields. | team_lead |
| `GET /sources/{sourceId}/events?limit=100` | Sample of the source's recent events, newest first, at most 200. Use it to offer field names in the rule editor. | user |

### Rules

| Method and path | Purpose | Role |
|---|---|---|
| `GET /rules?sourceId=` | List rules, optionally for one source | user |
| `POST /rules` | Create a rule | team_lead |
| `GET /rules/{ruleId}` | Read a rule | user |
| `PUT /rules/{ruleId}` | Replace a rule | team_lead |
| `DELETE /rules/{ruleId}` | Delete a rule | team_lead |
| `POST /rules/preview` | Evaluate `{ sourceId, match }` against the last 100 sampled events of the source: `{ matched: 7, total: 100, samples: [NormalizedEvent] }`. `samples` holds up to 5 matching events. | team_lead |
| `POST /ai/rule-suggestion` | Turn `{ sourceId, goal, sampleEvent }` into `{ match, severity, explanation, preview, warnings }`. `goal` is the user's description in plain language, in any language. `sampleEvent` is optional: an event the user picked as an example. `preview` has the shape of `POST /rules/preview`, `warnings` lists conditions on fields the source has not produced lately. | team_lead |

### Alerts

| Method and path | Purpose | Role |
|---|---|---|
| `GET /alerts?status=&severity=&ruleId=&limit=&cursor=` | List alerts, newest first, without `sampleEvent`. Filters are applied to each page after it is read, so a page can hold fewer items than `limit`. | user |
| `GET /alerts/{alertId}` | Alert with the `runbook` object embedded | user |
| `GET /alerts/{alertId}/timeline` | Timeline entries, oldest first | user |
| `POST /alerts/{alertId}/ack` | Acknowledge | user |
| `POST /alerts/{alertId}/resolve` | Resolve | user |
| `POST /alerts/{alertId}/assign` | Pass to `{ userId }` | user |
| `POST /alerts/{alertId}/notes` | Add a comment `{ text }` | user |
| `POST /alerts/{alertId}/resend` | Notify everyone in the rule's current routing again. Throttling and the rule's delivery window do not apply. Answers `409` when the rule was deleted. | team_lead |
| `PUT /alerts/{alertId}/runbook-steps` | Set which steps of the runbook checklist are done: `{ "checked": [0, 2] }` replaces the alert's `checkedSteps` | user |
| `DELETE /alerts/{alertId}` | Delete the alert and its timeline. Answers `204` and publishes `alert.deleted`. | team_lead |

The action endpoints return the updated alert and publish `alert.updated`.

### Groups and runbooks

| Method and path | Purpose | Role |
|---|---|---|
| `GET /groups`, `GET /groups/{groupId}` | Read recipient groups | user |
| `POST /groups`, `PUT /groups/{groupId}`, `DELETE /groups/{groupId}` | Manage recipient groups | team_lead |
| `GET /runbooks`, `GET /runbooks/{runbookId}` | Read runbooks | user |
| `POST /runbooks`, `PUT /runbooks/{runbookId}`, `DELETE /runbooks/{runbookId}` | Manage runbooks | team_lead |

### Push notifications

| Method and path | Purpose | Role |
|---|---|---|
| `GET /push/public-key` | `{ publicKey }`: the server's VAPID key a browser subscribes with. `503 PUSH_NOT_CONFIGURED` until the key pair exists. | any |
| `GET /me/push-subscriptions` | The caller's registered devices | any |
| `POST /me/push-subscriptions` | Register a device. The body is the browser's `PushSubscription.toJSON()`: `{ endpoint, keys: { p256dh, auth } }`. Registering the same device again replaces its entry. | any |
| `DELETE /me/push-subscriptions/{subscriptionId}` | Forget a device | any |

### Ingest

| Method and path | Purpose |
|---|---|
| `POST {ingest url}/ingest/{orgId}/{sourceId}?token={ingestToken}` | Push one payload from a webhook source |

- No JWT. The token may also be sent as the `X-Ingest-Token` header.
- The body is stored untouched as one record, whatever its content type.
- Returns `202 { "recordId": "..." }`.

There are two ingest URLs with the same path:

| Route | URL | Wrong token or unknown source |
|---|---|---|
| Direct (default) | `ingest_base_url` output, `VITE_INGEST_URL` | Still `202`. API Gateway writes to the stream without running any code, and the processor drops the payload when it reads it. |
| Through the ingest function | `api_url` output | `404` at once. Shares the account's Lambda concurrency, so it is throttled first under load. |

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

`type` is `alert.created`, `alert.updated` or `alert.deleted`. An update carries the whole alert
again, so the client replaces its copy. After `alert.deleted` the client drops its copy. An open alert window reloads its
timeline when an update for its alert arrives.

Notification message:

```json
{ "type": "notification", "alertId": "1795208362373-9f3a61c2", "severity": "INFO",
  "title": "payment-api: DB_TIMEOUT", "body": "Database connection timeout after 3000 ms on payment-api-1",
  "publishedAt": "2026-11-20T20:59:23.204Z" }
```

A subscriber is admitted only to the channels of their own organization, and
on `/notifications` only to their own channel. The organization is read from
the `custom:orgId` claim of the Cognito ID token, which the API sets when a
user creates, joins or leaves an organization and which users cannot change
themselves. A wildcard subscription such as `/alerts/*` is refused. Only the
Lambdas can publish.
