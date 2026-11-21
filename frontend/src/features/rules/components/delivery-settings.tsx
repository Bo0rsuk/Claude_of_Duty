import { ESCALATION_SECONDS } from "#services/backend/to-backend.ts";
import { Choices } from "#components/choices.tsx";
import { Field } from "#components/field.tsx";
import {
  type Severity,
  channels,
  homeTeam,
  severities,
  useWorkspaceContext,
} from "#features/workspace";
import { Plus, X } from "lucide-react";
import { useState } from "react";

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
                ? ["Phone", "SMS", "Push"]
                : severity === "Warning"
                  ? ["Email", "Push"]
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
/** An email address or a phone number in international form: a recipient who is not a member. */
const isAddress = (text: string) => /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(text) || /^\+\d{6,}$/.test(text);

export function Targets() {
  const { data, rule, editRule, account } = useWorkspaceContext(),
    [entry, setEntry] = useState(""),
    [error, setError] = useState("");
  const known = [
    // Roles such as "on call" and whole teams exist in the demo only.
    ...(account ? [] : ["On-call engineer", ...data.teams.filter((t) => t.id === homeTeam).map((t) => t.name)]),
    ...data.groups.map((g) => g.name),
    ...data.members.filter((m) => m.team === homeTeam && m.role !== "Pending").map((m) => m.name),
  ];
  function add() {
    const text = entry.trim();
    if (!text) return;
    const name = known.find((candidate) => candidate.toLowerCase() === text.toLowerCase()) ?? text;
    if (name === text && !known.includes(text) && !isAddress(text)) {
      setError("Enter a member, a group, an email address or a phone number such as +48600100200.");
      return;
    }
    if (!rule.targets.includes(name)) editRule({ targets: [...rule.targets, name] });
    setEntry("");
    setError("");
  }
  return (
    <>
      <fieldset>
        <legend>Recipients</legend>
        {rule.targets.length ? (
          <ul className="recipients">
            {rule.targets.map((name) => (
              <li key={name}>
                {name}
                <button
                  type="button"
                  aria-label={"Remove " + name}
                  title="Remove"
                  onClick={() => editRule({ targets: rule.targets.filter((other) => other !== name) })}
                >
                  <X />
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <p className="hint">
            {account ? "Nobody is notified. Alerts from this rule only appear in the feed." : "Nobody is notified yet."}
          </p>
        )}
        <div className="add-recipient">
          <input
            aria-label="Add a recipient"
            list="recipient-suggestions"
            value={entry}
            placeholder="Add a member, group, email or phone…"
            onChange={(e) => {
              setEntry(e.target.value);
              setError("");
            }}
            onKeyDown={(e) => {
              if (e.key !== "Enter") return;
              e.preventDefault();
              add();
            }}
          />
          <datalist id="recipient-suggestions">
            {known
              .filter((name) => !rule.targets.includes(name))
              .map((name) => (
                <option key={name} value={name} />
              ))}
          </datalist>
          <button type="button" aria-label="Add recipient" title="Add recipient" disabled={!entry.trim()} onClick={add}>
            <Plus />
          </button>
        </div>
        {error && (
          <p role="alert" className="error">
            {error}
          </p>
        )}
      </fieldset>
      <fieldset>
        <legend>Delivery channels</legend>
        <Choices
          items={channels}
          values={rule.channels}
          onChange={(channels) => editRule({ channels })}
        />
      </fieldset>
      <Field label="Escalate channel by channel · seconds between them">
        <input
          type="number"
          min={ESCALATION_SECONDS.min}
          max={ESCALATION_SECONDS.max}
          placeholder="All channels at once"
          value={rule.escalation ?? ""}
          onChange={(e) => editRule({ escalation: e.target.value })}
        />
      </Field>
      <p className="hint escalation-hint">
        {rule.escalation?.trim()
          ? "In-app and push go first, then email, SMS and the phone call, each after this pause. Channels the rule does not use are skipped. The rest is dropped as soon as someone acknowledges or resolves the alert."
          : "Leave empty to notify on every channel at the same moment."}
      </p>
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
            <option>No notifications</option>
          </select>
        </Field>
        <p className="hint">
          Times are in your time zone. Outside the window every recipient is notified on the chosen channel only. Leave
          the window empty to notify at any hour.
        </p>
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
