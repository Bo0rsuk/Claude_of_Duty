import { Modal } from "#components/modal.tsx";
import { ruleValidationError } from "#features/rules/domain/rule-validation.ts";
import { newRule, useWorkspaceContext } from "#features/workspace";
import { Save } from "lucide-react";
import { useState } from "react";

export function RuleActions() {
  const { rule, data, commit, setRule } = useWorkspaceContext(),
    [remove, setRemove] = useState(false),
    [error, setError] = useState("");
  function save() {
    const message = ruleValidationError(rule);
    setError(message);
    if (message) return;
    commit(
      { ...data, rules: [...data.rules.filter((r) => r.id !== rule.id), rule] },
      "Rule saved locally.",
    );
  }
  return (
    <>
      <div className="buttons">
        <button className="primary" onClick={save}>
          <Save />
          Save rule
        </button>
        {data.rules.some((r) => r.id === rule.id) && (
          <button className="danger-text" onClick={() => setRemove(true)}>
            Delete rule
          </button>
        )}
      </div>
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      {remove && (
        <Modal title="Delete rule?" onClose={() => setRemove(false)}>
          <p>
            Future demo events will no longer match “{rule.name}”. Existing
            alerts keep their history.
          </p>
          <div className="buttons">
            <button onClick={() => setRemove(false)}>Cancel</button>
            <button
              className="danger-btn"
              onClick={() => {
                const next = data.rules.filter((r) => r.id !== rule.id);
                commit({ ...data, rules: next }, "Rule deleted locally.");
                setRule(next[0] || newRule());
                setRemove(false);
              }}
            >
              Delete rule
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
