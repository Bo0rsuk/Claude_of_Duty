// Translates what the screens produce into the bodies the API accepts.

import type { Book, ChannelText, Data, Group, Rule, Source } from "#features/workspace/types/records.ts";
import type * as Api from "#services/backend/api-types.ts";
import { CONDITION_HELP, parseConditions } from "#services/backend/conditions.ts";
import type { Snapshot } from "#services/backend/snapshot.ts";
import { CHANNEL_LABELS } from "#services/backend/to-workspace.ts";

/** A change the user asked for that cannot be saved. The message is shown to the user. */
export class Unsupported extends Error {}

const list = (text: string) => text.split(",").map((item) => item.trim()).filter(Boolean);
const channelByLabel = (label: string) =>
  (Object.keys(CHANNEL_LABELS) as Api.Channel[]).find((channel) => CHANNEL_LABELS[channel] === label);

/** Which recipient a name on screen stands for. Names come from the data as it will be after the change. */
function target(name: string, data: Data, channel: Api.Channel): Api.Target | null {
  const group = data.groups.find((candidate) => candidate.name === name);
  if (group) return { type: "group", id: group.id };
  const member = data.members.find((candidate) => candidate.name === name);
  if (member) return { type: "user", id: member.id };
  // Anything else is an address typed in elsewhere; it only fits the channel of its kind.
  if (channel === "email" && name.includes("@")) return { type: "address", value: name };
  if ((channel === "sms" || channel === "voice") && /^\+\d{6,}$/.test(name)) return { type: "address", value: name };
  return null;
}

/** The per-channel texts as the API holds them. Empty parts are left out, so the channel uses the alert's own text. */
function channelTexts(texts: Record<string, ChannelText>): Api.Presentation["channels"] {
  const channels: NonNullable<Api.Presentation["channels"]> = {};
  for (const [label, text] of Object.entries(texts)) {
    const channel = channelByLabel(label);
    const subject = text.subject?.trim();
    const body = text.body?.trim();
    if (!channel || (!subject && !body)) continue;
    channels[channel] = { ...(subject ? { subject } : {}), ...(body ? { body } : {}) };
  }
  return Object.keys(channels).length ? channels : null;
}

/** The limit per recipient, or null when the fields were left empty. */
function recipientLimit(rule: Rule): Api.Limit | null {
  const max = Number(rule.recipientLimit);
  const minutes = Number(rule.recipientInterval);
  if (!rule.recipientLimit?.trim() && !rule.recipientInterval?.trim()) return null;
  if (!(max >= 1) || !(minutes >= 1)) {
    throw new Unsupported("Give the limit per recipient as a number of notifications and a number of minutes, or leave both empty.");
  }
  return { max: Math.round(max), windowSeconds: Math.round(minutes * 60) };
}

/** Why a delivery window cannot be used, or "" when it can. Empty means the rule notifies at any hour. */
export function deliveryWindowError(text: string) {
  return !text.trim() || parseWindow(text)
    ? ""
    : "Write the delivery window as two different times of day, for example 08:00-20:00, or leave it empty.";
}

function parseWindow(text: string) {
  // The two times may be joined with a hyphen or an en dash.
  const parts = text.trim().match(/^(\d{1,2}):(\d{2})\s*[-\u2013]\s*(\d{1,2}):(\d{2})\D*$/);
  if (!parts) return null;
  const [fromHour, fromMinute, toHour, toMinute] = parts.slice(1).map(Number);
  if (fromHour > 23 || toHour > 23 || fromMinute > 59 || toMinute > 59) return null;
  const clock = (hour: number, minute: number) => String(hour).padStart(2, "0") + ":" + String(minute).padStart(2, "0");
  const from = clock(fromHour, fromMinute);
  const to = clock(toHour, toMinute);
  return from === to ? null : { from, to };
}

/** The hours in which the rule's routing applies, and the channel used outside them. */
function delivery(rule: Rule, existing: Api.Rule | undefined): Api.Delivery | null {
  if (!rule.window?.trim()) return null;
  const times = parseWindow(rule.window);
  if (!times) throw new Unsupported(deliveryWindowError(rule.window));
  return {
    window: {
      ...times,
      // The times mean the clock of whoever first wrote them.
      timezone: existing?.delivery?.window.timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone,
    },
    outsideWindow: channelByLabel(rule.fallback) ?? null,
  };
}

export function ruleBody(rule: Rule, data: Data, snapshot: Snapshot): Api.RuleInput {
  const existing = snapshot.rules.find((candidate) => candidate.ruleId === rule.id);
  const source = snapshot.sources.find((candidate) => candidate.name === rule.source);
  if (!source) throw new Unsupported("Choose an event source for the rule.");
  const match = parseConditions(rule.conditions);
  if (!match) throw new Unsupported(CONDITION_HELP);

  const routing: Api.Route[] = [];
  for (const label of rule.channels) {
    const channel = channelByLabel(label);
    if (!channel) continue;
    for (const name of rule.targets) {
      const resolved = target(name, data, channel);
      if (resolved) routing.push({ channel, target: resolved });
    }
  }
  // A rule may notify nobody and only feed the alert list. But recipients that were chosen must be reachable.
  if (!routing.length && rule.targets.length) {
    throw new Unsupported("Choose a person or a group as the recipient, on a channel that can reach them.");
  }

  return {
    name: rule.name.trim(),
    sourceId: source.sourceId,
    enabled: rule.enabled ?? existing?.enabled ?? true,
    match,
    severity: rule.severity.toUpperCase() as Api.Severity,
    routing,
    presentation: {
      title: rule.template,
      // A draft that never touched these parts keeps what the rule had.
      body: rule.body?.trim() || existing?.presentation?.body || "{{message}}",
      fields: list(rule.fields),
      channels: rule.texts ? channelTexts(rule.texts) : (existing?.presentation?.channels ?? null),
    },
    groupBy: list(rule.group),
    throttle: {
      perRule: { max: Number(rule.limit), windowSeconds: Number(rule.interval) * 60 },
      perRecipient:
        rule.recipientLimit === undefined && rule.recipientInterval === undefined
          ? (existing?.throttle?.perRecipient ?? null)
          : recipientLimit(rule),
    },
    delivery: delivery(rule, existing),
    runbookId: rule.runbook || null,
  };
}

export function runbookBody(book: Book) {
  const title = book.name.trim();
  if (book.type === "Checklist") return { title, format: "checklist", items: book.body.split("\n").filter(Boolean) };
  if (book.type === "Wiki link") return { title, format: "link", url: book.body.trim() };
  return { title, format: book.type === "Markdown" ? "markdown" : "text", content: book.body };
}

export function groupBody(group: Group, snapshot: Snapshot) {
  const existing = snapshot.groups.find((candidate) => candidate.groupId === group.id);
  return {
    name: group.name.trim(),
    members: [
      ...group.members.map((userId) => ({ type: "user", userId })),
      ...(group.contacts
        ? group.contacts.map((contact) => ({
            type: "contact",
            name: contact.name.trim(),
            email: contact.email.trim() || null,
            phone: contact.phone.trim() || null,
          }))
        : // A group record without the list keeps the contacts it had.
          (existing?.members.filter((member) => member.type === "contact") ?? [])),
    ],
  };
}

/** Which event field holds the level, the message and the time, read from the reviewed mapping. */
function mappedFields(mapping: string) {
  const fields: { level?: string; message?: string; eventTime?: string } = {};
  for (const line of mapping.split("\n")) {
    const [from, to] = line.split("→").map((part) => part.trim());
    if (!from || !to) continue;
    if (to === "level" || to === "severity") fields.level = from;
    else if (to === "message") fields.message = from;
    else if (to === "time" || to === "timestamp" || to === "eventTime") fields.eventTime = from;
  }
  return fields;
}

export function parserFor(format: string, mapping: string): Api.ParserConfig | null {
  if (format === "JSON") return { format: "json", timeFormat: "iso8601", ...mappedFields(mapping) };
  if (format === "key=value") return { format: "kv", timeFormat: "iso8601", ...mappedFields(mapping) };
  // A custom format is kept as raw text: rules can still match on the message.
  return null;
}

/** The connector with the address typed on screen put into it. Everything else about it is kept. */
function withAddress(connector: Api.Connector, address: string): Api.Connector {
  const text = address.trim();
  if (connector.type === "http-pull") {
    if (!/^https?:\/\//.test(text)) throw new Unsupported("Enter the endpoint as an http or https address.");
    return { ...connector, url: text };
  }
  if (connector.type === "kafka") {
    const [bootstrapServers, topic] = text.replace(/^kafka:\/\//, "").split("/");
    if (!bootstrapServers || !topic) throw new Unsupported("Enter the Kafka connection as host:port/topic.");
    return { ...connector, bootstrapServers, topic };
  }
  return connector;
}

/** The body that replaces an existing source. The screens edit its name, address and whether it is on. */
export function sourceUpdateBody(source: Source, existing: Api.Source): Api.SourceInput {
  if (!source.id.trim()) throw new Unsupported("Give the source a name.");
  return {
    name: source.id.trim(),
    enabled: source.enabled ?? existing.enabled,
    connector: withAddress(existing.connector, source.connection),
    parser: existing.parser,
  };
}

export function sourceBody(source: Source): Api.SourceInput {
  if (source.preset) {
    return {
      name: source.id,
      connector: withAddress(source.preset.connector as Api.Connector, source.connection),
      parser: source.preset.parser as Api.ParserConfig | null,
    };
  }
  const parser = parserFor(source.format, source.mapping);
  if (source.type === "Webhook") return { name: source.id, connector: { type: "webhook" }, parser };
  if (source.type === "HTTP endpoint") {
    if (!/^https?:\/\//.test(source.connection)) throw new Unsupported("Enter the endpoint as an http or https address.");
    return { name: source.id, connector: { type: "http-pull", url: source.connection, intervalSeconds: 5 }, parser };
  }
  if (source.type === "Kafka topic") {
    const [bootstrapServers, topic] = source.connection.replace(/^kafka:\/\//, "").split("/");
    if (!bootstrapServers || !topic) throw new Unsupported("Enter the Kafka connection as host:port/topic.");
    return { name: source.id, connector: { type: "kafka", bootstrapServers, topic }, parser };
  }
  throw new Unsupported(source.type + " sources are not supported yet. Use a webhook, an HTTP endpoint or Kafka.");
}
