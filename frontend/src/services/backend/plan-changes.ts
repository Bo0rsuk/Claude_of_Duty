// The screens change data by producing a new copy of the whole workspace.
// This module compares the copy before and after and lists what has to be
// sent to the backend, so no screen needs to know about the API.

import type { Alert, Book, Data, Group, Member, Rule, Source } from "#features/workspace/types/records.ts";

export type Change =
  | { kind: "alert.acknowledge" | "alert.resolve" | "alert.resend"; alertId: string }
  | { kind: "alert.note"; alertId: string; text: string }
  | { kind: "alert.assign"; alertId: string; target: string }
  | { kind: "alert.checks"; alertId: string; checks: number[] }
  | { kind: "alert.delete"; alertId: string }
  | { kind: "rule.save"; rule: Rule; created: boolean }
  | { kind: "rule.delete"; ruleId: string }
  | { kind: "source.create"; source: Source }
  | { kind: "source.update"; source: Source }
  | { kind: "source.delete"; sourceId: string }
  | { kind: "book.save"; book: Book; created: boolean }
  | { kind: "book.delete"; bookId: string }
  | { kind: "group.save"; group: Group; created: boolean }
  | { kind: "group.delete"; groupId: string }
  | { kind: "member.create"; member: Member }
  | { kind: "member.role"; member: Member }
  | { kind: "member.profile"; member: Member }
  | { kind: "member.remove"; memberId: string }
  | { kind: "team.code" }
  /** Something the screens allow that the backend has no operation for. */
  | { kind: "unsupported"; message: string };

const same = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);

function byId<T extends { id: string }>(items: T[]) {
  return new Map(items.map((item) => [item.id, item]));
}

/** Changes to one kind of record that the screens create, edit and delete freely. */
function recordChanges<T extends { id: string }>(
  before: T[],
  after: T[],
  save: (item: T, created: boolean) => Change,
  remove: (id: string) => Change,
): Change[] {
  const previous = byId(before);
  const next = byId(after);
  return [
    ...after.filter((item) => !same(previous.get(item.id), item)).map((item) => save(item, !previous.has(item.id))),
    ...before.filter((item) => !next.has(item.id)).map((item) => remove(item.id)),
  ];
}

function alertChanges(before: Alert, after: Alert): Change[] {
  const changes: Change[] = [];
  const alertId = after.id;
  if (before.status !== after.status) {
    if (after.status === "Acknowledged") changes.push({ kind: "alert.acknowledge", alertId });
    else if (after.status === "Resolved") changes.push({ kind: "alert.resolve", alertId });
    else changes.push({ kind: "unsupported", message: "A resolved alert cannot be reopened." });
  }
  if (before.target !== after.target) changes.push({ kind: "alert.assign", alertId, target: after.target });
  if (!same(before.checks, after.checks)) changes.push({ kind: "alert.checks", alertId, checks: after.checks });
  // The screens record notes and resends as new timeline entries.
  for (const entry of after.timeline.slice(before.timeline.length)) {
    if (entry.title === "Note added") changes.push({ kind: "alert.note", alertId, text: entry.body });
    if (entry.title === "Sent") changes.push({ kind: "alert.resend", alertId });
  }
  return changes;
}

function memberChanges(before: Member, after: Member): Change[] {
  const changes: Change[] = [];
  if (before.team && !after.team) return [{ kind: "member.remove", memberId: after.id }];
  if (before.role !== after.role) {
    changes.push(
      after.role === "Pending"
        ? { kind: "unsupported", message: "A member always has a role. Remove the member instead." }
        : { kind: "member.role", member: after },
    );
  }
  if (before.name !== after.name || before.phone !== after.phone) changes.push({ kind: "member.profile", member: after });
  if (before.email !== after.email) {
    changes.push({ kind: "unsupported", message: "The email address is the sign-in name and cannot be changed here." });
  }
  return changes;
}

/** A source is known by its server id, so that renaming it is an update. On demo data the name is all there is. */
const sourceKey = (source: Source) => source.ref ?? source.id;

function sourceChanges(before: Source[], after: Source[]) {
  const previous = new Map(before.map((source) => [sourceKey(source), source]));
  const next = new Set(after.map(sourceKey));
  const changes: Change[] = [];
  /** Old name to new name. */
  const renamed = new Map<string, string>();
  for (const source of after) {
    const old = previous.get(sourceKey(source));
    if (!old) changes.push({ kind: "source.create", source });
    // When the last event arrived is not something the screens set.
    else if (!same({ ...old, last: 0 }, { ...source, last: 0 })) {
      changes.push({ kind: "source.update", source });
      if (old.id !== source.id) renamed.set(old.id, source.id);
    }
  }
  const removed = before.filter((source) => !next.has(sourceKey(source)));
  for (const source of removed) changes.push({ kind: "source.delete", sourceId: sourceKey(source) });
  return { changes, renamed, deleted: new Set(removed.map((source) => source.id)) };
}

export function planChanges(before: Data, after: Data): Change[] {
  const changes: Change[] = [];
  const previousAlerts = byId(before.alerts);
  for (const alert of after.alerts) {
    const previous = previousAlerts.get(alert.id);
    if (previous) changes.push(...alertChanges(previous, alert));
  }
  const remainingAlerts = byId(after.alerts);
  for (const alert of before.alerts) {
    if (!remainingAlerts.has(alert.id)) changes.push({ kind: "alert.delete", alertId: alert.id });
  }

  const previousMembers = byId(before.members);
  for (const member of after.members) {
    const previous = previousMembers.get(member.id);
    if (previous) changes.push(...memberChanges(previous, member));
    else if (member.team) changes.push({ kind: "member.create", member });
    else changes.push({ kind: "unsupported", message: "Not added: choose the team. An account is created as a member of it." });
  }

  const sources = sourceChanges(before.sources, after.sources);
  // Runbooks and groups first: a rule saved in the same step may refer to a new one.
  changes.push(
    ...recordChanges(before.books, after.books, (book, created) => ({ kind: "book.save", book, created }),
      (bookId) => ({ kind: "book.delete", bookId })),
    ...recordChanges(before.groups, after.groups, (group, created) => ({ kind: "group.save", group, created }),
      (groupId) => ({ kind: "group.delete", groupId })),
    ...sources.changes,
    // A renamed source is not a change to its rules, and the rules of a deleted source go with it.
    ...recordChanges(
      before.rules.map((rule) => ({ ...rule, source: sources.renamed.get(rule.source) ?? rule.source })),
      after.rules,
      (rule, created) => ({ kind: "rule.save", rule, created }),
      (ruleId) => ({ kind: "rule.delete", ruleId }),
    ).filter(
      (change) =>
        change.kind !== "rule.delete" ||
        !sources.deleted.has(before.rules.find((rule) => rule.id === change.ruleId)?.source ?? ""),
    ),
  );

  const previousTeams = byId(before.teams);
  for (const team of after.teams) {
    const previous = previousTeams.get(team.id);
    if (!previous) changes.push({ kind: "unsupported", message: "An account belongs to one organization. Teams cannot be added." });
    else if (previous.code !== team.code) changes.push({ kind: "team.code" });
    else if (previous.name !== team.name) changes.push({ kind: "unsupported", message: "The organization cannot be renamed yet." });
  }
  return changes;
}
