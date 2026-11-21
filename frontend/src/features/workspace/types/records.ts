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
  channels: string[];
  fields: Record<string, string>;
  runbook: string;
  checks: number[];
  timeline: Entry[];
}
export interface Source {
  id: string;
  type: string;
  format: string;
  connection: string;
  mapping: string;
  last: number;
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
export interface Group {
  id: string;
  name: string;
  members: string[];
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
