# Frontend integration guide

How the web application talks to the backend. The application in `frontend/`
follows this guide; its client code is in `frontend/src/services/backend/`.

| File | Use |
|---|---|
| [types.ts](types.ts) | TypeScript shapes of all requests, responses and real-time messages. Copy into the app. |
| [liveFeed.ts](liveFeed.ts) | WebSocket client for the live feed, no dependencies. Checked against the deployed stack. Copy into the app. |
| [push.ts](push.ts), [sw.js](sw.js) | Push notifications: enabling them on a device and the service worker that shows them. |
| [../contracts/api.md](../contracts/api.md) | Every endpoint with its minimum role. |

## 1. Configuration

```bash
terraform -chdir=infra/app output -raw frontend_env > frontend/.env
```

| Variable | Meaning |
|---|---|
| `VITE_AWS_REGION` | Region of the stack |
| `VITE_API_URL` | Base URL of the REST API, no trailing slash |
| `VITE_INGEST_URL` | Base URL that webhook senders post to. Only shown to users, the app does not call it. |
| `VITE_USER_POOL_ID`, `VITE_USER_POOL_CLIENT_ID` | Cognito user pool and its web client |
| `VITE_EVENTS_HTTP_ENDPOINT`, `VITE_EVENTS_REALTIME_ENDPOINT` | AppSync Events endpoints for the live feed |

CORS allows any origin for now, so `http://localhost:5173` works.

## 2. Signing in

Users sign up with email and password in Cognito. The application signs in
with plain `fetch` calls to Cognito. Send the ID token to the API: it reads
the user's email from it. Tokens last one hour.

## 3. Calling the API

Every request carries `Authorization: Bearer <ID token>`.

- Lists come as `{ items, nextCursor }`. Pass `?cursor=<nextCursor>` for the next page.
- Errors come as `{ error: { code, message } }`. `message` is written for people and can be shown as is.
- `403 FORBIDDEN` means the user's role is too low, or the user has no organization yet.

## 4. First run: organization

After sign-in call `GET /me`. While `org` is `null`, the user can do nothing
else and must see one of two forms:

| Form | Request | Result |
|---|---|---|
| Create an organization | `POST /orgs` `{ "name": "..." }` | The user becomes its `admin`. The response holds the `inviteCode` to share. |
| Join with a code | `POST /orgs/join` `{ "inviteCode": "ABCD-EFGH" }` | The user joins as `user`. A wrong code answers `404`. |

The demo organization has the code `DEMO-2026` and already receives alerts
from five sources.

Refresh the token after `POST /orgs` or `POST /orgs/join`, and once after
sign-in when `GET /me` returns `tokenRefreshNeeded`. The user's organization is
part of the ID token, and the real-time channels admit a subscriber only to the
channels of the organization in the token.

Roles decide what to show (the API enforces them regardless):

| Role | Sees |
|---|---|
| `user` | Alerts and their actions, read-only lists of everything else, own profile |
| `team_lead` | Also create, edit and delete for sources, rules, groups and runbooks |
| `admin` | Also members, roles and the invite code (`GET /org`, `GET /members`, `PATCH /members/{userId}`) |

`PATCH /me` with `{ "name", "phone" }` sets what notifications use to reach
the user. The phone number must be in the form `+48600100200`.

## 5. Live feed

Load the current alerts once with `GET /alerts?limit=100`, then keep them up
to date from the WebSocket channel `/alerts/${orgId}` using `subscribe` from
[liveFeed.ts](liveFeed.ts). Every message carries the whole alert.

- A subscription is accepted only for the user's own organization, and only with the ID token. A refused one arrives in `onError` as `subscribe_error` with the type `Unauthorized`: the token is stale, see section 4.
- `alert.created` is a new alert. `alert.updated` means its count, status, assignee or ticked runbook steps changed, or a note was added. `alert.deleted` means a team lead deleted it: drop it from the list instead of adding it.
- The client reconnects by itself. After a reconnect, reload `GET /alerts`, because messages sent while offline are not replayed. `onStatus(true)` is the moment to do it.
- An open alert window should reload its timeline when an update for its alert arrives.
- A burst of identical events sends many `alert.updated` messages for one alert, each with a higher `count`. Rendering must cope with several per second.

For pop-ups aimed at the signed-in user, subscribe the same way to
`/notifications/${orgId}/${userId}` with the type `NotificationMessage`, and
open the alert window for `alertId` when the pop-up is clicked. This is how a
rule's `ui` channel reaches the user.

## 6. Screens and the calls behind them

### Alert list and alert window

| Action | Call |
|---|---|
| List, newest first | `GET /alerts?status=&severity=&ruleId=&limit=&cursor=` |
| Open an alert | `GET /alerts/{alertId}` returns the alert, its first event (`sampleEvent`) and the `runbook` |
| History | `GET /alerts/{alertId}/timeline`, oldest first |
| Acknowledge | `POST /alerts/{alertId}/ack` (`409` if not open) |
| Resolve | `POST /alerts/{alertId}/resolve` |
| Pass to a colleague | `POST /alerts/{alertId}/assign` `{ "userId" }`, pick from `GET /members` |
| Comment | `POST /alerts/{alertId}/notes` `{ "text" }` |
| Notify everyone again | `POST /alerts/{alertId}/resend` (team lead), ignores the rule's throttling |
| Tick a runbook step | `PUT /alerts/{alertId}/runbook-steps` `{ "checked": [0, 2] }`, the whole list each time |
| Delete | `DELETE /alerts/{alertId}` (team lead), also removes the timeline |

Each action returns the updated alert. A link of the form
`<app url>/alerts/{alertId}` is what SMS and email contain, so the app needs
that route and must open the alert window on it.

`title`, `body` and `fields` are already rendered from the rule, so the window
shows them as given: this is how each rule controls how its alert looks.
Render the runbook by its `format`: `markdown`, `text`, `link` (a URL to open)
or `checklist` (tickable `items`).

### Sources

| Step | Call |
|---|---|
| List | `GET /sources` |
| "+" button: offer known types | `GET /source-presets` returns five ready `connector` + `parser` pairs |
| Custom type: try a pasted sample | `POST /sources/preview` `{ "parser", "sample" }` returns the parsed `events` |
| Save | `POST /sources`, `PUT /sources/{sourceId}` |
| Delete | `DELETE /sources/{sourceId}` also deletes its rules |

For a webhook source, show the URL the sender must call:
`${VITE_INGEST_URL}/ingest/${orgId}/${sourceId}?token=${ingestToken}`.

### Rule editor

| Step | Call |
|---|---|
| Field names to offer in conditions | `GET /sources/{sourceId}/events?limit=100`, take the keys of `fields` plus `level` and `message` |
| "Would catch X of the last 100" | `POST /rules/preview` `{ "sourceId", "match" }` |
| AI assistant | `POST /ai/rule-suggestion` `{ "sourceId", "goal", "sampleEvent"? }` |
| Recipients | `GET /groups`, `GET /members` |
| Runbook | `GET /runbooks`, or `POST /runbooks` to add one without leaving the editor |
| Save | `POST /rules`, `PUT /rules/{ruleId}` |

- The assistant answers in one to two seconds with `match`, a suggested `severity`, an `explanation` in the user's language, a `preview` and `warnings`. Put `match` into the condition builder for the user to confirm; nothing is saved by the call.
- Picking a severity should pre-select channels (`DEFAULT_CHANNELS` in `types.ts`), which the user can then change freely.
- A saved rule applies to new events within 15 seconds, with no restart.
- Validation errors answer `400` with the reason in `message`, e.g. an unknown operator or a broken regular expression.

### Groups, runbooks, members

Plain create, read, update, delete: see [../contracts/api.md](../contracts/api.md).

## 7. Hosting

`infra/app` creates an S3 bucket behind CloudFront for the built application:

```bash
scripts/deploy-frontend.sh
```

The script writes `frontend/.env` from the Terraform outputs, builds the
application, syncs `dist` to the bucket and invalidates the CloudFront cache.

Unknown paths are answered with `index.html`, so client-side routes such as
`/alerts/{alertId}` work on reload.

## 8. Installing as an app and push notifications

Push reaches the device even when the application is closed:

1. Copy [sw.js](sw.js) to the site root (`public/sw.js` in a Vite project). `/sw.js` is served without caching, so an update reaches users at once.
2. Add a button "Enable notifications on this device" that calls `enablePush(api)` from [push.ts](push.ts). It asks for permission, subscribes the browser and registers the device with `POST /me/push-subscriptions`. It must run from a click.
3. Offer `disablePush(api)` on the same screen.
4. In the rule editor, `push` is a channel like `sms` or `email`, with a user or a group as its target.

| Call | Purpose |
|---|---|
| `GET /push/public-key` | `{ publicKey }` the browser subscribes with |
| `GET /me/push-subscriptions` | The user's registered devices |
| `POST /me/push-subscriptions` | Register this device: send `subscription.toJSON()` as is |
| `DELETE /me/push-subscriptions/{subscriptionId}` | Forget a device |

- On iPhone and iPad, push works only after the site was added to the home screen and opened from there (iOS 16.4+). Say so in the UI on those devices.
- A user can enable push on several devices. Each gets every notification.
- A device whose browser unsubscribed is forgotten by the server on the next push.
- Clicking a notification opens `<app url>/alerts/{alertId}`, the same route SMS and email link to.

`demo/push-test/` is a standalone page for trying push on a phone.
