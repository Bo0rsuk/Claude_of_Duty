import { Choices } from "#components/choices.tsx";
import { Field } from "#components/field.tsx";
import {
  type Severity,
  channels,
  homeTeam,
  severities,
  useWorkspaceContext,
} from "#features/workspace";

export function SeveritySelector() {
  const { rule, editRule } = useWorkspaceContext();
  return (
    <Field label="Severity">
      <select
        value={rule.severity}
        onChange={(e) => {
          const severity = e.target.value as Severity;
          editRule({
            severity,
            channels:
              severity === "Critical"
                ? ["Phone", "SMS"]
                : severity === "Warning"
                  ? ["Email", "In-app"]
                  : ["In-app"],
          });
        }}
      >
        {severities.map((s) => (
          <option key={s}>{s}</option>
        ))}
      </select>
    </Field>
  );
}
export function Targets() {
  const { data, rule, editRule } = useWorkspaceContext();
  return (
    <>
      <fieldset>
        <legend>Recipients</legend>
        <Choices
          items={[
            "On-call engineer",
            ...data.teams.filter((t) => t.id === homeTeam).map((t) => t.name),
            ...data.groups.map((g) => g.name),
            ...data.members
              .filter((m) => m.team === homeTeam && m.role !== "Pending")
              .map((m) => m.name),
          ]}
          values={rule.targets}
          onChange={(targets) => editRule({ targets })}
        />
      </fieldset>
      <fieldset>
        <legend>Delivery channels</legend>
        <Choices
          items={channels}
          values={rule.channels}
          onChange={(channels) => editRule({ channels })}
        />
      </fieldset>
      <details>
        <summary>Delivery window & fallback</summary>
        <Field label="Local time window">
          <input
            value={rule.window}
            placeholder="08:00–20:00…"
            onChange={(e) => editRule({ window: e.target.value })}
          />
        </Field>
        <Field label="Outside this window">
          <select
            value={rule.fallback}
            onChange={(e) => editRule({ fallback: e.target.value })}
          >
            {channels.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
        </Field>
      </details>
    </>
  );
}
export function Grouping() {
  const { rule, editRule } = useWorkspaceContext();
  return (
    <>
      <Field label="Group events by">
        <input
          value={rule.group}
          onChange={(e) => editRule({ group: e.target.value })}
        />
      </Field>
      <Field label="Grouping window (minutes)">
        <input
          type="number"
          min="1"
          value={rule.interval}
          onChange={(e) => editRule({ interval: e.target.value })}
        />
      </Field>
      <Field label="Maximum notifications per window">
        <input
          type="number"
          min="1"
          value={rule.limit}
          onChange={(e) => editRule({ limit: e.target.value })}
        />
      </Field>
    </>
  );
}
