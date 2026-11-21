import { Empty } from "#components/empty.tsx";
import { Field } from "#components/field.tsx";
import { addAction } from "#features/alerts/utils/add-action.ts";
import { useWorkspaceContext } from "#features/workspace";
import { clock } from "#lib/time.ts";
import { useState } from "react";

export function Timeline() {
  const { alert: a, data, commit } = useWorkspaceContext(),
    [note, setNote] = useState("");
  if (!a)
    return (
      <Empty title="Select an alert">
        Choose an alert to see its timeline.
      </Empty>
    );
  return (
    <div className="timeline">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (!note.trim()) return;
          commit(
            {
              ...data,
              alerts: data.alerts.map((x) =>
                x.id === a.id ? addAction(x, "Note added", note.trim()) : x,
              ),
            },
            "Note added locally.",
          );
          setNote("");
        }}
      >
        <Field label="Team note">
          <input
            value={note}
            required
            onChange={(e) => setNote(e.target.value)}
            placeholder="Add response context…"
          />
        </Field>
        <button>Add note</button>
      </form>
      <ol>
        {[...a.timeline].reverse().map((item, i) => (
          <li key={i}>
            <div>
              <strong>{item.title}</strong>
              <time>{clock(item.time)}</time>
            </div>
            <span>{item.body}</span>
          </li>
        ))}
      </ol>
    </div>
  );
}
