export type Role = "User" | "Team lead" | "Admin";
export type Panel =
  | "Alerts"
  | "Custom"
  | "Rules"
  | "Sources"
  | "Groups & runbooks"
  | "Users"
  | "Admin";
export type Severity = "Critical" | "Warning" | "Info";
export type Status = "Open" | "Acknowledged" | "Resolved";

export interface Entry {
  time: number;
  title: string;
  body: string;
}
/** The first event grouped into an alert, as the parser read it. */
export interface SampleEvent {
  time: number;
  level: string;
  message: string;
  fields: Record<string, string>;
  raw: string;
}
export interface Alert {
  id: string;
  title: string;
  severity: Severity;
  status: Status;
  source: string;
  rule: string;
  first: number;
  last: number;
  count: number;
  target: string;
  /** The person working on the alert, "" when nobody took it. Absent on demo data, where target is the owner. */
  owner?: string;
  /** The people and groups the rule notifies. Absent on demo data. */
  notified?: string[];
  channels: string[];
  fields: Record<string, string>;
  runbook: string;
  checks: number[];
  timeline: Entry[];
  /** Loaded when the alert is opened. Absent on demo data. */
  sample?: SampleEvent;
}
export interface Source {
  id: string;
  type: string;
  format: string;
  connection: string;
  mapping: string;
  last: number;
  /** False when the source is switched off. Absent means on. */
  enabled?: boolean;
  /** The id the server knows the source by. It stays the same when the source is renamed. */
  ref?: string;
  /** A ready connector and parser taken from the server's presets. Sent as they are when the source is created. */
  preset?: { connector: object; parser: object | null };
}
/** The text of a rule's notification on one channel. Empty parts fall back to the alert's title and body. */
export interface ChannelText {
  subject?: string;
  body?: string;
}
export interface Rule {
  id: string;
  name: string;
  source: string;
  severity: Severity;
  conditions: string;
  targets: string[];
  channels: string[];
  window: string;
  fallback: string;
  group: string;
  interval: string;
  limit: string;
  runbook: string;
  template: string;
  fields: string;
  /** False when the rule is switched off. Absent means on. */
  enabled?: boolean;
  /** Template of the alert's body text. */
  body?: string;
  /** Texts that replace the title and body on single channels, by channel label. */
  texts?: Record<string, ChannelText>;
  /** Most notifications one recipient gets on one channel per window. Both empty means no limit. */
  recipientLimit?: string;
  /** Length of that window in minutes. */
  recipientInterval?: string;
}
export interface Member {
  id: string;
  name: string;
  email: string;
  phone: string;
  team: string;
  role: Role | "Pending";
  duty: boolean;
}
export interface Team {
  name: string;
  id: string;
  lead: string;
  code: string;
}
export interface Book {
  id: string;
  name: string;
  type: string;
  body: string;
}
/** A person outside the organization who is notified as part of a group. */
export interface Contact {
  name: string;
  email: string;
  phone: string;
}
export interface Group {
  id: string;
  name: string;
  members: string[];
  contacts?: Contact[];
}
export interface Data {
  alerts: Alert[];
  sources: Source[];
  rules: Rule[];
  members: Member[];
  teams: Team[];
  books: Book[];
  groups: Group[];
}
