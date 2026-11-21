import assert from "node:assert/strict";
import test from "node:test";
import { planChanges } from "#services/backend/plan-changes.ts";

const alert = { id: "a1", status: "Open", target: "Anna", timeline: [{ time: 1, title: "Alert opened", body: "Rule" }], checks: [] };
const rule = { id: "r1", name: "Errors", targets: ["Anna"] };
const base = {
  alerts: [alert],
  rules: [rule],
  sources: [{ id: "payments" }],
  books: [{ id: "b1", name: "Recovery", body: "step" }],
  groups: [{ id: "g1", name: "Ops", members: ["anna"] }],
  members: [{ id: "anna", name: "Anna", email: "anna@example.pl", phone: "", team: "platform", role: "User" }],
  teams: [{ id: "platform", name: "Ops", lead: "anna", code: "AAAA-BBBB" }],
};
const withAlert = (changes) => ({ ...base, alerts: [{ ...alert, ...changes }] });
const kinds = (after, before = base) => planChanges(before, after).map((change) => change.kind);

test("what the alert screens do becomes alert operations", () => {
  const acknowledged = withAlert({ status: "Acknowledged", timeline: [...alert.timeline, { time: 2, title: "Acknowledged", body: "x" }] });
  assert.deepEqual(planChanges(base, acknowledged), [{ kind: "alert.acknowledge", alertId: "a1" }]);
  assert.deepEqual(kinds(withAlert({ status: "Resolved" })), ["alert.resolve"]);
  assert.deepEqual(kinds(withAlert({ status: "Open" }), withAlert({ status: "Resolved" })), ["unsupported"]);

  const noted = withAlert({ timeline: [...alert.timeline, { time: 2, title: "Note added", body: "Restarting the pool" }] });
  assert.deepEqual(planChanges(base, noted), [{ kind: "alert.note", alertId: "a1", text: "Restarting the pool" }]);
  const resent = withAlert({ timeline: [...alert.timeline, { time: 2, title: "Sent", body: "Simulated resend" }] });
  assert.deepEqual(kinds(resent), ["alert.resend"]);
  assert.deepEqual(planChanges(base, withAlert({ target: "Bob" })), [{ kind: "alert.assign", alertId: "a1", target: "Bob" }]);

  // Ticked runbook steps are stored with the alert. Opening the alert changes nothing on the server.
  assert.deepEqual(planChanges(base, withAlert({ checks: [0] })), [{ kind: "alert.checks", alertId: "a1", checks: [0] }]);
  assert.deepEqual(kinds(withAlert({ timeline: [...alert.timeline, { time: 2, title: "Viewed", body: "" }] })), []);
});

test("an alert removed on screen is deleted", () => {
  assert.deepEqual(planChanges(base, { ...base, alerts: [] }), [{ kind: "alert.delete", alertId: "a1" }]);
});

test("created, edited and deleted records are told apart", () => {
  const edited = { ...base, rules: [{ ...rule, name: "All errors" }, { id: "r2", name: "New" }] };
  assert.deepEqual(planChanges(base, edited), [
    { kind: "rule.save", rule: { ...rule, name: "All errors" }, created: false },
    { kind: "rule.save", rule: { id: "r2", name: "New" }, created: true },
  ]);
  assert.deepEqual(planChanges(base, { ...base, rules: [] }), [{ kind: "rule.delete", ruleId: "r1" }]);
  assert.deepEqual(kinds({ ...base, books: [], groups: [{ id: "g1", name: "Ops", members: [] }] }), ["book.delete", "group.save"]);
  assert.deepEqual(kinds({ ...base, sources: [...base.sources, { id: "orders" }] }), ["source.create"]);
});

test("a runbook created together with a rule is saved before the rule", () => {
  const after = { ...base, books: [...base.books, { id: "b2", name: "New" }], rules: [{ ...rule, runbook: "b2" }] };
  assert.deepEqual(kinds(after), ["book.save", "rule.save"]);
});

test("member and organization changes map to what the backend offers", () => {
  const member = (changes) => ({ ...base, members: [{ ...base.members[0], ...changes }] });
  assert.deepEqual(kinds(member({ role: "Team lead" })), ["member.role"]);
  assert.deepEqual(kinds(member({ name: "Anna Nowak", phone: "+48600100200" })), ["member.profile"]);
  assert.deepEqual(kinds(member({ team: "", role: "Pending" })), ["member.remove"]);
  assert.deepEqual(kinds(member({ role: "Pending" })), ["unsupported"]);
  assert.deepEqual(kinds(member({ email: "other@example.pl" })), ["unsupported"]);

  const team = (changes) => ({ ...base, teams: [{ ...base.teams[0], ...changes }] });
  assert.deepEqual(kinds(team({ code: "CCCC-DDDD" })), ["team.code"]);
  assert.deepEqual(kinds(team({ name: "Renamed" })), ["unsupported"]);
  assert.deepEqual(kinds({ ...base, teams: [...base.teams, { id: "t2", name: "Markets", code: "X" }] }), ["unsupported"]);
});

test("an account created by an admin is added to the team, and needs one", () => {
  const person = { id: "new", name: "Bo", email: "bo@example.com", phone: "", role: "User", duty: false };
  const before = { alerts: [], members: [], books: [], groups: [], rules: [], sources: [], teams: [] };
  assert.deepEqual(planChanges(before, { ...before, members: [{ ...person, team: "platform" }] }).map((c) => c.kind), ["member.create"]);
  assert.deepEqual(planChanges(before, { ...before, members: [{ ...person, team: "" }] }).map((c) => c.kind), ["unsupported"]);
});

test("a source is edited, switched off, renamed and deleted by its server id", () => {
  const source = { id: "payments", ref: "s1", connection: "https://a.example/events", enabled: true, last: 1 };
  const before = { ...base, sources: [source], rules: [{ ...rule, source: "payments" }] };
  const withSource = (changes) => ({ ...before, sources: [{ ...source, ...changes }] });

  assert.deepEqual(planChanges(before, withSource({ enabled: false })), [
    { kind: "source.update", source: { ...source, enabled: false } },
  ]);
  // The time of the last event is derived on screen and is not an edit.
  assert.deepEqual(kinds(withSource({ last: 2 }), before), []);

  // The screens write the new name into the source's rules; only the source is sent.
  const renamed = { ...withSource({ id: "billing" }), rules: [{ ...rule, source: "billing" }] };
  assert.deepEqual(kinds(renamed, before), ["source.update"]);

  // The server deletes the rules of a deleted source itself.
  assert.deepEqual(planChanges(before, { ...before, sources: [], rules: [] }), [{ kind: "source.delete", sourceId: "s1" }]);
  const other = { id: "r2", name: "Other", source: "orders" };
  const two = { ...before, sources: [source, { id: "orders", ref: "s2" }], rules: [...before.rules, other] };
  assert.deepEqual(kinds({ ...two, sources: [two.sources[1]], rules: [] }, two), ["source.delete", "rule.delete"]);
});
