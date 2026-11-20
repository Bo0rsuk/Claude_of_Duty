# Data model

DynamoDB, on-demand capacity, one table per entity. Table names carry the
`cod-` prefix. Configuration tables are keyed by `orgId`, so every API read is
a single-partition query scoped to the caller's organization.

| Table | Partition key | Sort key | Extra | Holds |
|---|---|---|---|---|
| `cod-orgs` | `orgId` | - | GSI `inviteCode-index` on `inviteCode` | Organization name and invite code |
| `cod-members` | `orgId` | `userId` | GSI `userId-index` on `userId` | Membership, role, name, email, phone |
| `cod-sources` | `orgId` | `sourceId` | - | Source definitions |
| `cod-rules` | `orgId` | `ruleId` | - | Rules |
| `cod-groups` | `orgId` | `groupId` | - | Recipient groups |
| `cod-runbooks` | `orgId` | `runbookId` | - | Runbook library |
| `cod-alerts` | `orgId` | `alertId` | - | Alerts |
| `cod-alert-timeline` | `alertId` | `entryId` | - | Timeline entries |
| `cod-dedup` | `fingerprint` | - | - | Pointer from fingerprint to the active `alertId` |
| `cod-counters` | `counterKey` | - | TTL on `expiresAt` | Throttling counters |
| `cod-recent-events` | `sourceId` | `eventKey` | TTL on `expiresAt` | Sample of recent events for the rule preview |

Item shapes are the JSON objects from the other contract files, stored as-is.

## Access patterns

| Who | Needs | How |
|---|---|---|
| API | Organization of the logged-in user | `cod-members` GSI `userId-index` |
| API | Join by invite code | `cod-orgs` GSI `inviteCode-index` |
| API | List sources, rules, groups, runbooks, members | Query by `orgId` |
| API | Live feed, newest first | Query `cod-alerts` by `orgId`, descending. `alertId` starts with the creation time. Status and severity are filter expressions. |
| API | Alert history | Query `cod-alert-timeline` by `alertId`, ascending |
| Ingest | Validate a webhook source and its token | Get `cod-sources` by `orgId` + `sourceId`, cached |
| Processor | Parser of a source | Get `cod-sources` by `orgId` + `sourceId`, cached 15 s |
| Processor | Rules of a source | Query `cod-rules` by `orgId`, cached 15 s, filtered by `sourceId` in memory |
| Processor | Deduplicate | Conditional put on `cod-dedup` |
| Processor | Throttle | Conditional update on `cod-counters` |
| Notifier | Expand a group | Get `cod-groups`, then `cod-members` for user contacts |
| Worker | All pull and Kafka sources | Scan `cod-sources` every 30 s |

## Entities not defined elsewhere

Organization:

```json
{ "orgId": "org-1", "name": "Cloud team", "inviteCode": "K7Q2-M9XD", "createdAt": "2026-11-20T21:00:00.000Z" }
```

Member:

```json
{ "orgId": "org-1", "userId": "cognito sub", "role": "team_lead",
  "name": "Anna Nowak", "email": "anna@example.pl", "phone": "+48600100200", "joinedAt": "2026-11-20T21:00:00.000Z" }
```

Recipient group:

```json
{ "groupId": "grp-cloud-engs", "orgId": "org-1", "name": "cloud_engs",
  "members": [
    { "type": "user", "userId": "usr-1" },
    { "type": "contact", "name": "NOC", "email": "noc@example.pl", "phone": "+48221002000" }
  ],
  "createdAt": "2026-11-20T21:00:00.000Z", "updatedAt": "2026-11-20T21:00:00.000Z" }
```

A `user` member uses the contact details from `cod-members`. A `contact` is
someone outside the organization.

## Notes

- **`cod-dedup`:** the item is `{ fingerprint, alertId, orgId }`. Created with `attribute_not_exists(fingerprint)`, deleted when the alert is resolved.
- **`cod-counters`:** `counterKey` is described in [alerts.md](alerts.md). `expiresAt` is the window end plus one hour, in epoch seconds.
- **`cod-recent-events`:** `eventKey` is `<eventTime>#<eventId>`. The processor writes a sample, at most a few events per second per source, and items expire after one hour. This table exists only for the rule preview and is the first thing to drop if time runs out.
