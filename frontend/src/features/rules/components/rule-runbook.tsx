import { Field } from "#components/field.tsx";
import { Modal } from "#components/modal.tsx";
import { useWorkspaceContext } from "#features/workspace";
import { unique } from "#lib/id.ts";
import { Plus } from "lucide-react";
import { useState } from "react";

export function RuleRunbook() {
  const { data, commit, rule, editRule } = useWorkspaceContext(),
    [name, setName] = useState(""),
    [draft, setDraft] = useState(false);
  return (
    <>
      <Field label="Assigned runbook">
        <select
          value={rule.runbook}
          onChange={(e) => editRule({ runbook: e.target.value })}
        >
          <option value="">No runbook</option>
          {data.books.map((b) => (
            <option key={b.id} value={b.id}>
              {b.name}
            </option>
          ))}
        </select>
      </Field>
      <button onClick={() => setDraft(true)}>
        <Plus />
        Draft runbook
      </button>
      {draft && (
        <Modal title="Draft runbook" onClose={() => setDraft(false)}>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              const id = unique();
              commit(
                {
                  ...data,
                  books: [
                    ...data.books,
                    {
                      id,
                      name: name.trim(),
                      body: "Review the latest event.",
                      type: "Checklist",
                    },
                  ],
                },
                "Runbook created locally.",
              );
              editRule({ runbook: id });
              setDraft(false);
              setName("");
            }}
          >
            <Field label="Runbook title">
              <input
                required
                pattern=".*\S.*"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </Field>
            <button className="primary">Create runbook</button>
          </form>
        </Modal>
      )}
    </>
  );
}
