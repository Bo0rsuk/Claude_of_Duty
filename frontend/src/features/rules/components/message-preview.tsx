import { Field } from "#components/field.tsx";
import { useWorkspaceContext } from "#features/workspace";
import { useState } from "react";

export function MessagePreview() {
  const { data, rule, editRule } = useWorkspaceContext(),
    [view, setView] = useState("Feed"),
    fields = data.alerts.find((a) => a.source === rule.source)?.fields ?? {};
  const title = rule.template.replace(
    /\{\{(\w+)\}\}/g,
    (_, key) => fields[key] ?? (key === "service" ? rule.source : "sample"),
  );
  return (
    <>
      <Field label="Title template">
        <input
          value={rule.template}
          onChange={(e) => editRule({ template: e.target.value })}
        />
      </Field>
      <Field label="Visible fields">
        <input
          value={rule.fields}
          onChange={(e) => editRule({ fields: e.target.value })}
        />
      </Field>
      <div className="segmented">
        {["Feed", "SMS", "Email"].map((v) => (
          <button key={v} aria-pressed={v === view} onClick={() => setView(v)}>
            {v}
          </button>
        ))}
      </div>
      <div className="preview-block">
        <small>{view} preview</small>
        <h4>
          {view === "Email" ? "Subject · " : view === "SMS" ? "FANS · " : ""}
          {title}
        </h4>
        <p>
          {rule.fields
            .split(",")
            .map((k) => k.trim() + " = " + (fields[k.trim()] ?? "sample"))
            .join(" · ")}
        </p>
      </div>
    </>
  );
}
