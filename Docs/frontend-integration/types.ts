/**
 * TypeScript shapes of everything the API sends and accepts.
 * The source of truth is Docs/contracts; keep this file in step with it.
 * Timestamps are ISO-8601 strings in UTC, e.g. "2026-11-20T20:59:22.373Z".
 */

export type Role = 'user' | 'team_lead' | 'admin';
export type Severity = 'CRITICAL' | 'WARNING' | 'INFO';
export type Level = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR' | 'FATAL' | 'UNKNOWN';
export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
export type Channel = 'sms' | 'voice' | 'email' | 'ui' | 'push';
export type FieldValue = string | number | boolean;

/** Lists come wrapped. `nextCursor` is absent on the last page. */
export type Page<T> = { items: T[]; nextCursor?: string };

/** Every failed request has this body. */
export type ApiError = { error: { code: string; message: string } };

// ---------------------------------------------------------------- organization

export type Profile = {
  userId: string;
  email: string;
  name: string | null;
  phone: string | null;
  /** Null until the user creates or joins an organization. */
  org: { orgId: string; name: string; role: Role } | null;
  /**
   * True when the ID token does not carry the user's current organization yet. Get a new token
   * (fetchAuthSession({ forceRefresh: true })) before subscribing to real-time channels.
   */
  tokenRefreshNeeded: boolean;
};

export type Organization = {
  orgId: string;
  name: string;
  /** Present for admins only. */
  inviteCode: string | null;
  createdAt: string;
};

export type Member = {
  orgId: string;
  userId: string;
  role: Role;
  name: string | null;
  email: string | null;
  phone: string | null;
  joinedAt: string;
};

// --------------------------------------------------------------------- sources

export type Connector =
  | { type: 'webhook' }
  | { type: 'http-pull'; url: string; intervalSeconds?: number; itemsPath?: string | null; cursorParam?: string; cursorFrom?: string }
  | { type: 'kafka'; bootstrapServers: string; topic: string; groupId?: string };

export type ParserConfig = {
  format: 'json' | 'regex';
  /** regex only: Java regular expression with named groups (letters and digits in names). */
  pattern?: string | null;
  /** json only: path to an array whose elements become separate events. */
  split?: string | null;
  eventTime?: string | null;
  /** "iso8601", "epoch_millis" or a Java DateTimeFormatter pattern. */
  timeFormat?: string | null;
  level?: string | null;
  message?: string | null;
};

export type Source = {
  sourceId: string;
  orgId: string;
  name: string;
  enabled: boolean;
  connector: Connector;
  parser: ParserConfig | null;
  /** Webhook sources only. The sender posts to /ingest/{orgId}/{sourceId}?token={ingestToken}. */
  ingestToken: string | null;
  createdAt: string;
  updatedAt: string;
};

/** What POST and PUT /sources accept. The server fills in the rest. */
export type SourceInput = Pick<Source, 'name' | 'connector'> & Partial<Pick<Source, 'enabled' | 'parser'>>;

/** GET /source-presets: a source with only name, connector and parser filled in. */
export type SourcePreset = Pick<Source, 'name' | 'connector' | 'parser'>;

export type NormalizedEvent = {
  eventId: string;
  orgId: string;
  sourceId: string;
  eventTime: string;
  receivedAt: string;
  level: Level;
  message: string;
  /** Flat map. Nested JSON is flattened with dots, e.g. "http.status". */
  fields: Record<string, FieldValue>;
  raw: string;
};

// ----------------------------------------------------------------------- rules

export type Operator = 'eq' | 'neq' | 'in' | 'contains' | 'regex' | 'gt' | 'gte' | 'lt' | 'lte' | 'exists';

export type Condition = {
  /** "level", "message" or a key of the event's fields. */
  field: string;
  op: Operator;
  /** An array for "in", a number for the comparisons, a boolean for "exists". */
  value: FieldValue | FieldValue[];
};

export type Match = { mode: 'all' | 'any'; conditions: Condition[] };

export type Target =
  | { type: 'group'; id: string }
  | { type: 'user'; id: string }
  /** A phone number in E.164 format or an email address. Not valid for the "ui" and "push" channels. */
  | { type: 'address'; value: string };

export type Route = { channel: Channel; target: Target };

export type Presentation = {
  /** Templates use {{name}} placeholders: any event field, level, message, severity, ruleName, sourceName, count, alertUrl. */
  title: string;
  body: string;
  /** Event fields shown as a table in the alert window. */
  fields: string[];
  /** Optional text per channel. */
  channels?: Partial<Record<Channel, { subject?: string; body?: string }>> | null;
};

export type Limit = { max: number; windowSeconds: number };

export type Rule = {
  ruleId: string;
  orgId: string;
  sourceId: string;
  name: string;
  enabled: boolean;
  match: Match;
  severity: Severity;
  routing: Route[];
  presentation: Presentation | null;
  /** Events with the same values of these fields are grouped into one alert. */
  groupBy: string[];
  throttle: { perRule?: Limit | null; perRecipient?: Limit | null } | null;
  runbookId: string | null;
  createdAt: string;
  updatedAt: string;
  updatedBy: string | null;
};

export type RuleInput = Omit<Rule, 'ruleId' | 'orgId' | 'createdAt' | 'updatedAt' | 'updatedBy' | 'enabled'> & { enabled?: boolean };

/** POST /rules/preview */
export type RulePreview = { matched: number; total: number; samples: NormalizedEvent[] };

/** POST /ai/rule-suggestion */
export type RuleSuggestion = {
  match: Match;
  severity: Severity;
  /** In the language the goal was written in. */
  explanation: string;
  preview: RulePreview;
  /** E.g. a condition on a field the source has not produced lately. */
  warnings: string[];
};

/** Channels the UI pre-selects when a severity is picked. The rule may hold any combination. */
export const DEFAULT_CHANNELS: Record<Severity, Channel[]> = {
  CRITICAL: ['sms', 'voice', 'push'],
  WARNING: ['email', 'push'],
  INFO: ['ui'],
};

// -------------------------------------------------------- groups and runbooks

export type GroupMember =
  | { type: 'user'; userId: string }
  | { type: 'contact'; name: string; email?: string | null; phone?: string | null };

export type RecipientGroup = {
  groupId: string;
  orgId: string;
  name: string;
  members: GroupMember[];
  createdAt: string;
  updatedAt: string;
};

export type Runbook = {
  runbookId: string;
  orgId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
} & (
  | { format: 'markdown' | 'text'; content: string }
  | { format: 'link'; url: string }
  | { format: 'checklist'; items: string[] }
);

// ---------------------------------------------------------------------- alerts

export type Alert = {
  alertId: string;
  orgId: string;
  ruleId: string;
  ruleName: string;
  sourceId: string;
  fingerprint: string;
  severity: Severity;
  status: AlertStatus;
  /** Already rendered from the rule's presentation. */
  title: string;
  body: string;
  fields: Record<string, FieldValue>;
  /** Number of events grouped into this alert. */
  count: number;
  firstSeenAt: string;
  lastSeenAt: string;
  /** Null in lists and live feed messages, present in GET /alerts/{alertId}. */
  sampleEvent: NormalizedEvent | null;
  runbookId: string | null;
  assigneeId: string | null;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  resolvedBy: string | null;
  resolvedAt: string | null;
  timing: { receivedAt: string; processedAt: string };
};

/** GET /alerts/{alertId} */
export type AlertDetails = Alert & { runbook: Runbook | null };

export type TimelineEntry = {
  alertId: string;
  entryId: string;
  at: string;
  type:
    | 'CREATED'
    | 'NOTIFICATION_SENT'
    | 'NOTIFICATION_FAILED'
    | 'NOTIFICATION_SUPPRESSED'
    | 'NOTIFICATIONS_RESENT'
    | 'ACKNOWLEDGED'
    | 'ASSIGNED'
    | 'RESOLVED'
    | 'NOTE';
  actor: { type: 'system' | 'user'; id: string | null; name: string | null };
  /** Depends on the type, see Docs/contracts/alerts.md. E.g. { text } for NOTE, { channel, recipient, attempt } for notifications. */
  detail: Record<string, unknown>;
};

// ------------------------------------------------------------------------ push

/** One device on which the user enabled push notifications. */
export type PushSubscriptionRecord = {
  userId: string;
  subscriptionId: string;
  endpoint: string;
  keys: { p256dh: string; auth: string };
  createdAt: string;
};

/** What the service worker receives in a push event. */
export type PushMessage = { title: string; body: string; alertId: string; severity: Severity; url: string };

// ------------------------------------------------------------------- real time

/** Messages on the channel /alerts/{orgId}. An update carries the whole alert: replace your copy. */
export type AlertMessage = {
  type: 'alert.created' | 'alert.updated';
  alert: Alert;
  publishedAt: string;
};

/** Messages on the channel /notifications/{orgId}/{userId}: show a pop-up that opens the alert. */
export type NotificationMessage = {
  type: 'notification';
  alertId: string;
  severity: Severity;
  title: string;
  body: string;
  publishedAt: string;
};
