import { Field } from "#components/field.tsx";
import { Modal } from "#components/modal.tsx";
import { Save } from "#components/save.tsx";
import { type Source, newRule, useWorkspaceContext } from "#features/workspace";
import { plural } from "#lib/plural.ts";
import { Pencil, Power, Trash2 } from "lucide-react";
import { useState } from "react";

/** Switching a source off and on, renaming it, changing its address and deleting it. */
export function SourceActions({ source }: { source: Source }) {
  const { data, commit, setSourceId, rule, setRule, sourceFilter, setSourceFilter, account } = useWorkspaceContext();
  const [editing, setEditing] = useState(false),
    [removing, setRemoving] = useState(false),
    [name, setName] = useState(source.id),
    [connection, setConnection] = useState(source.connection),
    [error, setError] = useState("");
  const on = source.enabled !== false;
  const linked = data.rules.filter((r) => r.source === source.id);
  // The server assigns a webhook's address, so there is nothing to type.
  const fixedAddress = Boolean(account) && source.type === "Webhook";

  /** Replaces the source. Rules and alerts refer to a source by its name, so a new name is written to them too. */
  function replace(next: Source, message: string) {
    const renamed = <T extends { source: string }>(item: T) =>
      item.source === source.id && next.id !== source.id ? { ...item, source: next.id } : item;
    commit(
      {
        ...data,
        sources: data.sources.map((s) => (s === source ? next : s)),
        rules: data.rules.map(renamed),
        alerts: data.alerts.map(renamed),
      },
      message,
    );
    if (next.id !== source.id) {
      setSourceId(next.id);
      if (rule.source === source.id) setRule(renamed(rule));
      if (sourceFilter === source.id) setSourceFilter(next.id);
    }
  }

  return (
    <>
      <div className="buttons">
        <button
          onClick={() =>
            replace({ ...source, enabled: !on }, on ? "Source turned off locally." : "Source turned on locally.")
          }
        >
          <Power />
          {on ? "Turn off" : "Turn on"}
        </button>
        <button
          onClick={() => {
            setName(source.id);
            setConnection(source.connection);
            setError("");
            setEditing(true);
          }}
        >
          <Pencil />
          Edit
        </button>
        <button className="danger-text" onClick={() => setRemoving(true)}>
          <Trash2 />
          Delete
        </button>
      </div>
      {!on && (
        <p className="helper">
          This source is off: its events are not read and its rules raise no alerts. Turn it on to resume.
        </p>
      )}
      {editing && (
        <Modal title="Edit source" onClose={() => setEditing(false)}>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              const id = name.trim();
              if (data.sources.some((s) => s !== source && s.id === id)) {
                setError("Another source has this name. Choose a unique name.");
                return;
              }
              replace(
                { ...source, id, connection: fixedAddress ? source.connection : connection.trim() },
                "Source saved locally.",
              );
              setEditing(false);
            }}
          >
            <Field label="Source name">
              <input required pattern=".*\S.*" value={name} onChange={(e) => setName(e.target.value)} />
            </Field>
            {fixedAddress ? (
              <p className="helper">
                A webhook keeps the address it was given. To get a new address, delete the source and add it again.
              </p>
            ) : (
              <Field label={account ? "Connection address" : "Demo connection address"}>
                <input required value={connection} onChange={(e) => setConnection(e.target.value)} />
              </Field>
            )}
            {error && (
              <p role="alert" className="error">
                {error}
              </p>
            )}
            <div className="buttons">
              <button type="button" onClick={() => setEditing(false)}>
                Cancel
              </button>
              <Save label="Save source" />
            </div>
          </form>
        </Modal>
      )}
      {removing && (
        <Modal title="Delete source?" onClose={() => setRemoving(false)}>
          <p>
            Events from “{source.id}” will no longer be read
            {linked.length
              ? `, and ${plural(linked.length, "rule")} that ${linked.length === 1 ? "matches" : "match"} them will be deleted with it`
              : ""}
            . Existing alerts keep their history.
          </p>
          <div className="buttons">
            <button onClick={() => setRemoving(false)}>Cancel</button>
            <button
              className="danger-btn"
              onClick={() => {
                const sources = data.sources.filter((s) => s !== source);
                const rules = data.rules.filter((r) => r.source !== source.id);
                commit({ ...data, sources, rules }, "Source deleted locally.");
                setSourceId(sources[0]?.id ?? "");
                if (rule.source === source.id) setRule(rules[0] || newRule(account ? { ...data, sources, rules } : undefined));
                if (sourceFilter === source.id) setSourceFilter("");
                setRemoving(false);
              }}
            >
              Delete source
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
