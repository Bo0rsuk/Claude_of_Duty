// What the backend holds for the signed-in user's organization, plus the few
// things only this application keeps.

import type * as Api from "#services/backend/api-types.ts";

/** Everything loaded from the API, in the backend's own shapes. */
export type Snapshot = {
  profile: Api.Profile;
  organization: Api.Organization | null;
  sources: Api.Source[];
  rules: Api.Rule[];
  groups: Api.RecipientGroup[];
  runbooks: Api.Runbook[];
  members: Api.Member[];
  alerts: Api.Alert[];
  /** Where the next page of older alerts starts, or null when every alert is loaded. */
  olderAlerts: string | null;
};

/** Details the backend has no place for. They stay in this browser. */
export type Extras = {
  /** Ticked runbook steps per alert. */
  checks: Record<string, number[]>;
  /** Delivery window settings per rule. */
  windows: Record<string, { window: string; fallback: string }>;
};

export const noExtras = (): Extras => ({ checks: {}, windows: {} });

/** The full records of the alerts that have been opened, by alert id. They carry the first event. */
export type Details = Record<string, Api.AlertDetails>;

/** Timelines of the alerts that have been opened, by alert id. */
export type Timelines = Record<string, Api.TimelineEntry[]>;
