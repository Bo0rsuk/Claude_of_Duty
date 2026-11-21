// Loads the workspace from the API and carries out planned changes.

import type { Data } from "#features/workspace/types/records.ts";
import type * as Api from "#services/backend/api-types.ts";
import { ApiFailure, api } from "#services/backend/http.ts";
import type { Change } from "#services/backend/plan-changes.ts";
import type { Snapshot } from "#services/backend/snapshot.ts";
import {
  Unsupported,
  groupBody,
  ruleBody,
  runbookBody,
  sourceBody,
  sourceUpdateBody,
} from "#services/backend/to-backend.ts";
import { ROLE_LABELS } from "#services/backend/to-workspace.ts";

const ALERT_PAGE = 200;

const items = async <T>(path: string) => (await api<Api.Page<T>>("GET", path)).items;

export const loadProfile = () => api<Api.Profile>("GET", "/me");

export async function loadSnapshot(profile: Api.Profile): Promise<Snapshot> {
  const [organization, sources, rules, groups, runbooks, members, alerts] = await Promise.all([
    api<Api.Organization>("GET", "/org"),
    items<Api.Source>("/sources"),
    items<Api.Rule>("/rules"),
    items<Api.RecipientGroup>("/groups"),
    items<Api.Runbook>("/runbooks"),
    items<Api.Member>("/members"),
    api<Api.Page<Api.Alert>>("GET", "/alerts?limit=" + ALERT_PAGE),
  ]);
  return {
    profile,
    organization,
    sources,
    rules,
    groups,
    runbooks,
    members,
    alerts: alerts.items,
    olderAlerts: alerts.nextCursor ?? null,
  };
}

/** The page of alerts that follows the ones already loaded. */
export const loadOlderAlerts = (cursor: string) =>
  api<Api.Page<Api.Alert>>("GET", `/alerts?limit=${ALERT_PAGE}&cursor=${encodeURIComponent(cursor)}`);

/** The alert with its first event and its runbook. */
export const loadAlertDetails = (alertId: string) => api<Api.AlertDetails>("GET", `/alerts/${alertId}`);

export const loadSourcePresets = () => items<Api.SourcePreset>("/source-presets");

export const loadDevices = () => items<Api.PushSubscriptionRecord>("/me/push-subscriptions");
export const forgetDevice = (subscriptionId: string) =>
  api<void>("DELETE", `/me/push-subscriptions/${subscriptionId}`);

export const loadTimeline = (alertId: string) => items<Api.TimelineEntry>(`/alerts/${alertId}/timeline`);

/** Ids the server assigned to records the screens created under their own temporary ids. */
export type Created = { rules: Record<string, string>; books: Record<string, string>; groups: Record<string, string> };

/** True when the server answered that it has no such endpoint: it runs a version from before the feature. */
const missingEndpoint = (failure: unknown) =>
  failure instanceof ApiFailure && failure.status === 404 && failure.message.startsWith("No such endpoint");

/**
 * Sends the changes one after another, stopping at the first failure.
 *
 * @param data the workspace as it will be after the change, for resolving names
 * @returns notes for the user about changes that have no backend operation, and the alerts the server sent back
 */
export async function applyChanges(changes: Change[], data: Data, snapshot: Snapshot) {
  const created: Created = { rules: {}, books: {}, groups: {} };
  const notes: string[] = [];
  const alerts: Api.Alert[] = [];
  const roleOf = (label: string) =>
    (Object.keys(ROLE_LABELS) as Api.Role[]).find((role) => ROLE_LABELS[role] === label);

  for (const change of changes) {
    switch (change.kind) {
      case "alert.acknowledge":
        await api("POST", `/alerts/${change.alertId}/ack`);
        break;
      case "alert.resolve":
        await api("POST", `/alerts/${change.alertId}/resolve`);
        break;
      case "alert.resend":
        await api("POST", `/alerts/${change.alertId}/resend`);
        break;
      case "alert.note":
        await api("POST", `/alerts/${change.alertId}/notes`, { text: change.text });
        break;
      case "alert.assign": {
        const member = data.members.find((candidate) => candidate.name === change.target);
        if (!member) throw new Unsupported("An alert can be passed to a person, not to a group.");
        await api("POST", `/alerts/${change.alertId}/assign`, { userId: member.id });
        break;
      }
      case "alert.checks":
        try {
          alerts.push(await api<Api.Alert>("PUT", `/alerts/${change.alertId}/runbook-steps`, { checked: change.checks }));
        } catch (failure) {
          // An older server keeps no runbook progress. It stays in this browser, as before.
          if (!missingEndpoint(failure)) throw failure;
        }
        break;
      case "alert.delete":
        try {
          await api("DELETE", `/alerts/${change.alertId}`);
        } catch (failure) {
          if (!missingEndpoint(failure)) throw failure;
          // An older server cannot delete alerts. Closing it is the nearest thing.
          const stored = snapshot.alerts.find((candidate) => candidate.alertId === change.alertId);
          if (stored && stored.status !== "RESOLVED") await api("POST", `/alerts/${change.alertId}/resolve`);
          notes.push("This server keeps alerts for their history, so the alert was resolved instead.");
        }
        break;
      case "rule.save": {
        // A runbook created in the same step is referred to by its temporary id.
        const rule = { ...change.rule, runbook: created.books[change.rule.runbook] ?? change.rule.runbook };
        const body = ruleBody(rule, data, snapshot);
        if (change.created) created.rules[rule.id] = (await api<Api.Rule>("POST", "/rules", body)).ruleId;
        else await api("PUT", `/rules/${rule.id}`, body);
        break;
      }
      case "rule.delete":
        await api("DELETE", `/rules/${change.ruleId}`);
        break;
      case "source.create":
        await api("POST", "/sources", sourceBody(change.source));
        break;
      case "source.update": {
        const existing = snapshot.sources.find((candidate) => candidate.sourceId === change.source.ref);
        if (!existing) throw new Unsupported("This source no longer exists. Reload the page.");
        await api("PUT", `/sources/${existing.sourceId}`, sourceUpdateBody(change.source, existing));
        break;
      }
      case "source.delete":
        await api("DELETE", `/sources/${change.sourceId}`);
        break;
      case "book.save":
        if (change.created) {
          created.books[change.book.id] = (await api<Api.Runbook>("POST", "/runbooks", runbookBody(change.book))).runbookId;
        } else await api("PUT", `/runbooks/${change.book.id}`, runbookBody(change.book));
        break;
      case "book.delete":
        await api("DELETE", `/runbooks/${change.bookId}`);
        break;
      case "group.save":
        if (change.created) {
          created.groups[change.group.id] = (
            await api<Api.RecipientGroup>("POST", "/groups", groupBody(change.group, snapshot))
          ).groupId;
        } else await api("PUT", `/groups/${change.group.id}`, groupBody(change.group, snapshot));
        break;
      case "group.delete":
        await api("DELETE", `/groups/${change.groupId}`);
        break;
      case "member.create":
        await api("POST", "/members", {
          email: change.member.email,
          name: change.member.name,
          role: roleOf(change.member.role),
        });
        notes.push("A temporary password was emailed to " + change.member.email + ".");
        break;
      case "member.role":
        await api("PATCH", `/members/${change.member.id}`, { role: roleOf(change.member.role) });
        break;
      case "member.profile":
        {
          const own = change.member.id === snapshot.profile.userId;
          const name = change.member.name.trim();
          const phone = change.member.phone.trim();
          const email = change.member.email.trim().toLowerCase();
          const before = snapshot.members.find((member) => member.userId === change.member.id);
          const emailChanged = email !== (before?.email ?? "").toLowerCase();
          const saved = own
            ? await api<Api.Profile>("PATCH", "/me", { name, phone, ...(emailChanged && { email }) })
            : await api<Api.Member>("PATCH", `/members/${change.member.id}`, {
                role: roleOf(change.member.role),
                name,
                phone,
                ...(emailChanged && { email }),
              });
          // An older server ignores the parts it does not know and answers with the record unchanged.
          if (emailChanged && (saved.email ?? "").toLowerCase() !== email) {
            throw new Unsupported("The email address cannot be changed until the backend is updated. Name and phone were saved.");
          }
          if (!own && ((saved.name ?? "") !== name || (saved.phone ?? "") !== phone)) {
            throw new Unsupported("This server lets each person set only their own name and phone.");
          }
          if (emailChanged) {
            notes.push(
              own
                ? "Sign in with " + email + " from now on."
                : "If this person has an account, they sign in with " + email + " from now on.",
            );
          }
        }
        break;
      case "member.remove":
        await api("DELETE", `/members/${change.memberId}`);
        break;
      case "team.code":
        await api("POST", "/org/invite-code");
        break;
      case "unsupported":
        notes.push(change.message);
        break;
    }
  }
  return { created, notes, alerts };
}
