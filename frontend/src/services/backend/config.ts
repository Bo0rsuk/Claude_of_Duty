// Where the backend lives. The values come from `terraform output frontend_env`.
// Without them the application runs as the local prototype on demo data.

type Env = Partial<Record<string, string>>;
// Vite provides the values in the browser; scripts and tests run under Node read the process environment.
const env: Env =
  (import.meta as { env?: Env }).env ?? (globalThis as { process?: { env: Env } }).process?.env ?? {};

export const backend = {
  region: env.VITE_AWS_REGION ?? "",
  apiUrl: env.VITE_API_URL ?? "",
  ingestUrl: env.VITE_INGEST_URL ?? "",
  userPoolClientId: env.VITE_USER_POOL_CLIENT_ID ?? "",
  eventsHttpEndpoint: env.VITE_EVENTS_HTTP_ENDPOINT ?? "",
  eventsRealtimeEndpoint: env.VITE_EVENTS_REALTIME_ENDPOINT ?? "",
};

/** True when the application talks to the real backend. */
export const live = Boolean(backend.apiUrl && backend.userPoolClientId);
