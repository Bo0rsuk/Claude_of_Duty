// Translates the backend's records into the shapes the screens were built on.

import type {
  Alert,
  Book,
  Data,
  Entry,
  Group,
  Member,
  Role,
  Rule,
  Severity,
  Source,
  Status,
} from "#features/workspace/types/records.ts";
import type * as Api from "#services/backend/api-types.ts";
import { backend } from "#services/backend/config.ts";
import { formatConditions } from "#services/backend/conditions.ts";
import type { Details, Extras, Snapshot, Timelines } from "#services/backend/snapshot.ts";

/** The screens know one team; in the live application that is the user's organization. */
export const TEAM_ID = "platform";

const capitalized = <T extends string>(value: string) => (value[0] + value.slice(1).toLowerCase()) as T;
const time = (iso: string | null | undefined) => (iso ? Date.parse(iso) : 0);

export const CHANNEL_LABELS: Record<Api.Channel, string> = {
  voice: "Phone",
  sms: "SMS",
  email: "Email",
  ui: "In-app",
  push: "Push",
};
/** The choice "outside the delivery window, notify nobody". */
export const NO_FALLBACK = "No notifications";
export const ROLE_LABELS: Record<Api.Role, Role> = { user: "User", team_lead: "Team lead", admin: "Admin" };
const BOOK_TYPES: Record<Api.Runbook["format"], string> = {
  checklist: "Checklist",
  markdown: "Markdown",
  link: "Wiki link",
  text: "Plain text",
};
const SOURCE_TYPES: Record<Api.Connector["type"], string> = {
  webhook: "Webhook",
  "http-pull": "HTTP endpoint",
  kafka: "Kafka topic",
};

export const memberName = (member: Api.Member) => member.name || member.email || member.userId;

/** The name shown for a source. Rules and alerts refer to sources by this name on screen. */
export const sourceName = (snapshot: Snapshot, sourceId: string) =>
  snapshot.sources.find((source) => source.sourceId === sourceId)?.name ?? sourceId;

function targetName(snapshot: Snapshot, target: Api.Target): string {
  if (target.type === "group") return snapshot.groups.find((group) => group.groupId === target.id)?.name ?? target.id;
  if (target.type === "user") {
    const member = snapshot.members.find((candidate) => candidate.userId === target.id);
    return member ? memberName(member) : target.id;
  }
  return target.value;
}

/** The address of a connector as the screens show and edit it. A webhook has none until the source exists. */
export function connectorAddress(connector: Api.Connector): string {
  if (connector.type === "http-pull") return connector.url;
  if (connector.type === "kafka") return connector.bootstrapServers + "/" + connector.topic;
  return "";
}

function connection(source: Api.Source): string {
  if (source.connector.type !== "webhook") return connectorAddress(source.connector);
  return `${backend.ingestUrl}/ingest/${source.orgId}/${source.sourceId}?token=${source.ingestToken ?? ""}`;
}

export const sourceType = (connector: Api.Connector) => SOURCE_TYPES[connector.type] ?? connector.type;
export const sourceFormat = (parser: Api.ParserConfig | null) =>
  parser?.format === "json" ? "JSON" : parser?.format === "kv" ? "key=value" : "Custom format";


export function mapping(parser: Api.ParserConfig | null): string {
  if (!parser) return "Events are kept as raw text";
  const lines = [
    parser.level && parser.level + " → level",
    parser.message && parser.message + " → message",
    parser.eventTime && parser.eventTime + " → time",
    parser.split && "one event per item of " + parser.split,
    parser.pattern && "pattern: " + parser.pattern,
  ].filter(Boolean);
  return lines.length ? lines.join("\n") : "All fields keep their names";
}

function toSource(snapshot: Snapshot, source: Api.Source): Source {
  const lastAlert = Math.max(
    0,
    ...snapshot.alerts.filter((alert) => alert.sourceId === source.sourceId).map((alert) => time(alert.lastSeenAt)),
  );
  return {
    id: source.name,
    type: sourceType(source.connector),
    format: sourceFormat(source.parser),
    connection: connection(source),
    mapping: mapping(source.parser),
    last: lastAlert || time(source.updatedAt),
    enabled: source.enabled,
    ref: source.sourceId,
  };
}

export function toRule(snapshot: Snapshot, rule: Api.Rule, extras: Extras): Rule {
  const unique = (values: string[]) => [...new Set(values)];
  const limit = rule.throttle?.perRule;
  const each = rule.throttle?.perRecipient;
  // A backend that knows delivery windows always sends the field. Before that the setting lived in this browser.
  const kept = extras.windows[rule.ruleId];
  const delivery = rule.delivery;
  return {
    id: rule.ruleId,
    name: rule.name,
    source: sourceName(snapshot, rule.sourceId),
    severity: capitalized<Severity>(rule.severity),
    conditions: formatConditions(rule.match),
    targets: unique((rule.routing ?? []).map((route) => targetName(snapshot, route.target))),
    channels: unique((rule.routing ?? []).map((route) => CHANNEL_LABELS[route.channel] ?? route.channel)),
    window: delivery === undefined ? (kept?.window ?? "") : delivery ? delivery.window.from + "-" + delivery.window.to : "",
    fallback:
      delivery === undefined
        ? (kept?.fallback ?? "Email")
        : delivery
          ? delivery.outsideWindow
            ? CHANNEL_LABELS[delivery.outsideWindow]
            : NO_FALLBACK
          : (kept?.fallback ?? "Email"),
    group: (rule.groupBy ?? []).join(", "),
    interval: String(limit ? Math.max(1, Math.round(limit.windowSeconds / 60)) : 5),
    limit: String(limit?.max ?? 10),
    runbook: rule.runbookId ?? "",
    template: rule.presentation?.title ?? rule.name,
    fields: (rule.presentation?.fields ?? []).join(", "),
    enabled: rule.enabled,
    body: rule.presentation?.body ?? "{{message}}",
    texts: Object.fromEntries(
      Object.entries(rule.presentation?.channels ?? {}).map(([channel, text]) => [
        CHANNEL_LABELS[channel as Api.Channel] ?? channel,
        { subject: text?.subject ?? "", body: text?.body ?? "" },
      ]),
    ),
    recipientLimit: each ? String(each.max) : "",
    recipientInterval: each ? String(Math.max(1, Math.round(each.windowSeconds / 60))) : "",
    escalation: rule.escalation ? String(rule.escalation.stepSeconds) : "",
  };
}

function toBook(runbook: Api.Runbook): Book {
  const body =
    runbook.format === "checklist" ? runbook.items.join("\n") : runbook.format === "link" ? runbook.url : runbook.content;
  return { id: runbook.runbookId, name: runbook.title, type: BOOK_TYPES[runbook.format], body: body ?? "" };
}

function toGroup(group: Api.RecipientGroup): Group {
  return {
    id: group.groupId,
    name: group.name,
    members: group.members.flatMap((member) => (member.type === "user" ? [member.userId] : [])),
    contacts: group.members.flatMap((member) =>
      member.type === "contact" ? [{ name: member.name, email: member.email ?? "", phone: member.phone ?? "" }] : [],
    ),
  };
}

function toMember(member: Api.Member): Member {
  return {
    id: member.userId,
    name: memberName(member),
    email: member.email ?? "",
    phone: member.phone ?? "",
    team: TEAM_ID,
    role: ROLE_LABELS[member.role],
    duty: false,
  };
}

/** One line of an alert's history, worded for the timeline. */
export function toEntry(entry: Api.TimelineEntry): Entry {
  const detail = entry.detail as Record<string, string | number | boolean | undefined>;
  const who = entry.actor.name ?? "the system";
  const delivery = `${CHANNEL_LABELS[detail.channel as Api.Channel] ?? detail.channel} to ${detail.recipient}`;
  const [title, body] = (
    {
      CREATED: ["Alert opened", String(detail.ruleName ?? "")],
      NOTIFICATION_SENT: [
        "Sent",
        delivery + (detail.latencyMs !== undefined ? ` · ${detail.latencyMs} ms after the event arrived` : "") +
          (detail.simulated ? " · simulated" : ""),
      ],
      NOTIFICATION_FAILED: [
        detail.final ? "Failed" : "Retrying",
        `${delivery} · attempt ${detail.attempt} · ${detail.error}`,
      ],
      NOTIFICATION_SUPPRESSED: [
        "Suppressed",
        `${delivery} ${
          detail.reason === "alertHandled"
            ? "not needed: the alert was acknowledged or resolved before this step of the escalation"
            : detail.reason === "deliveryWindow"
              ? "held back outside the rule's delivery window"
              : "held back by the rule's limit"
        }`,
      ],
      NOTIFICATIONS_RESENT: ["Sent", `${detail.notifications} notifications sent again by ${who}`],
      ACKNOWLEDGED: ["Acknowledged", "Acknowledged by " + who],
      ASSIGNED: ["Reassigned", `Passed to ${detail.assigneeName} by ${who}`],
      RESOLVED: ["Resolved", "Resolved by " + who],
      NOTE: ["Note added", `${detail.text} · ${who}`],
    } as Record<string, [string, string]>
  )[entry.type] ?? [entry.type, ""];
  return { time: time(entry.at), title, body };
}

function toAlert(snapshot: Snapshot, alert: Api.Alert, extras: Extras, timelines: Timelines, details: Details): Alert {
  const sample = details[alert.alertId]?.sampleEvent;
  const rule = snapshot.rules.find((candidate) => candidate.ruleId === alert.ruleId);
  const shown = rule ? toRule(snapshot, rule, extras) : null;
  const assignee = snapshot.members.find((member) => member.userId === alert.assigneeId);
  const first = time(alert.firstSeenAt);
  return {
    id: alert.alertId,
    title: alert.title,
    severity: capitalized<Severity>(alert.severity),
    status: capitalized<Status>(alert.status),
    source: sourceName(snapshot, alert.sourceId),
    rule: alert.ruleId,
    first,
    last: time(alert.lastSeenAt),
    count: alert.count,
    target: assignee ? memberName(assignee) : (shown?.targets[0] ?? "Unassigned"),
    owner: assignee ? memberName(assignee) : "",
    notified: shown?.targets ?? [],
    channels: shown?.channels ?? [],
    fields: Object.fromEntries(Object.entries(alert.fields ?? {}).map(([key, value]) => [key, String(value)])),
    runbook: alert.runbookId ?? "",
    // A backend that stores ticked steps always sends the field. Before that they lived in this browser.
    checks: alert.checkedSteps === undefined ? (extras.checks[alert.alertId] ?? []) : (alert.checkedSteps ?? []),
    // The full history is loaded when the alert is opened.
    timeline: timelines[alert.alertId]?.map(toEntry) ?? [{ time: first, title: "Alert opened", body: alert.ruleName }],
    ...(sample && {
      sample: {
        time: time(sample.eventTime),
        level: sample.level,
        message: sample.message,
        fields: Object.fromEntries(Object.entries(sample.fields ?? {}).map(([key, value]) => [key, String(value)])),
        raw: sample.raw,
      },
    }),
  };
}

export function toWorkspace(snapshot: Snapshot, extras: Extras, timelines: Timelines, details: Details = {}): Data {
  const lead = snapshot.members.find((member) => member.role !== "user");
  return {
    alerts: snapshot.alerts.map((alert) => toAlert(snapshot, alert, extras, timelines, details)),
    sources: snapshot.sources.map((source) => toSource(snapshot, source)),
    rules: snapshot.rules.map((rule) => toRule(snapshot, rule, extras)),
    members: snapshot.members.map(toMember),
    teams: [
      {
        id: TEAM_ID,
        name: snapshot.organization?.name ?? snapshot.profile.org?.name ?? "",
        lead: lead?.userId ?? "",
        code: snapshot.organization?.inviteCode ?? "Shown to admins",
      },
    ],
    books: snapshot.runbooks.map(toBook),
    groups: snapshot.groups.map(toGroup),
  };
}
