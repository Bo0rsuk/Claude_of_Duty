import { Field } from "#components/field.tsx";
import { homeTeam, useWorkspaceContext } from "#features/workspace";

export const controllingLists: Record<string, string> = {
  details: "feed",
  manage: "feed",
  timeline: "feed",
  instructions: "feed",
  rule: "rules",
  assistant: "rules",
  severity: "rules",
  targets: "rules",
  grouping: "rules",
  rulebook: "rules",
  preview: "rules",
  ruleActions: "rules",
  source: "sources",
  members: "groups",
  book: "books",
  user: "users",
  assign: "users",
  team: "teams",
  code: "teams",
};

export function ContextPicker({ list }: { list: string }) {
  const app = useWorkspaceContext(),
    { data } = app;
  type Option = { id: string; name: string };
  type Selector = [string, string, (id: string) => void, Option[]];
  const selectors: Record<string, Selector> = {
    feed: [
      "Selected alert",
      app.alertId,
      app.selectAlert,
      data.alerts.map((a) => ({ id: a.id, name: a.id + " · " + a.title })),
    ],
    rules: [
      "Selected rule",
      app.rule.id,
      (id) => {
        const r = data.rules.find((r) => r.id === id);
        if (r) app.setRule(r);
      },
      data.rules,
    ],
    sources: [
      "Selected source",
      app.sourceId,
      app.setSourceId,
      data.sources.map((s) => ({ id: s.id, name: s.id })),
    ],
    groups: ["Selected group", app.groupId, app.setGroupId, data.groups],
    books: ["Selected runbook", app.bookId, app.setBookId, data.books],
    users: [
      "Selected member",
      app.memberId,
      app.setMemberId,
      data.members.filter((m) => app.role === "Admin" || m.team === homeTeam),
    ],
    teams: ["Selected team", app.teamId, app.setTeamId, data.teams],
  };
  const [label, value, choose, options] = selectors[list];
  return (
    <div className="context-picker">
      <Field label={label}>
        <select value={value} onChange={(e) => choose(e.target.value)}>
          <option value="" disabled>
            Choose a record
          </option>
          {options.map((o) => (
            <option key={o.id} value={o.id}>
              {o.name}
            </option>
          ))}
        </select>
      </Field>
    </div>
  );
}
