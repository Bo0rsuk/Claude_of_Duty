# Squawk frontend

The web application: React 19, TypeScript and Vite. Live at https://squawk.com.pl.

## Run

Node.js 22.12 or newer.

```sh
npm ci
npm run dev
```

Checks: `npm test`, `npm run typecheck`, `npm run lint`, `npm run build`.

## Demo mode and live mode

Without a `.env` file the application runs on local sample data, with a role
switcher and **Prototype controls** in the profile menu. With a `.env` it talks
to the deployed backend: Cognito sign-in, REST API, live feed over WebSocket,
the rule assistant and push notifications.

```sh
terraform -chdir=../infra/app output -raw frontend_env > .env
npm run dev
```

Publish to S3 and CloudFront with `../scripts/deploy-frontend.sh`.

## Roles

| Role | Access |
| --- | --- |
| User | Alerts and Custom view |
| Team lead | Also Rules, Sources, Groups, Runbooks and Users |
| Admin | Also Admin: all accounts, teams and invite codes |

## Code

| Path | Contents |
| --- | --- |
| `src/features/` | One directory per screen: alerts, rules, sources, groups and runbooks, members, admin, canvas |
| `src/services/backend/` | Backend client: `gateway.ts` (reads and writes), `to-workspace.ts` and `to-backend.ts` (API shapes to UI data and back), `plan-changes.ts` (a diff of two states as API calls), `conditions.ts` (condition text to rule structure and back) |
| `src/features/workspace/services/live-workspace.ts` | State of live mode |
| `src/features/workspace/services/demo-workspace.ts` | Sample data of demo mode |

In live mode, deleting an alert closes it and keeps its history. Recipient
availability windows and the fallback channel are local only and are not sent
to the backend.

Demo mode keeps its changes in `localStorage` under keys starting with `fans:`.
Delete them and reload to reset.
