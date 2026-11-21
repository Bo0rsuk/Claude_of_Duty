import { Badge } from "#components/badge.tsx";
import { Box } from "#components/box.tsx";
import { Empty } from "#components/empty.tsx";
import { Field } from "#components/field.tsx";
import { Modal } from "#components/modal.tsx";
import { newRule, useWorkspaceContext } from "#features/workspace";
import { GitBranch, Plus } from "lucide-react";
import { useState } from "react";

export function RuleList() {
  const {
      data,
      rule,
      setRule,
      sourceFilter,
      setSourceFilter,
      setCompactDetail,
      account,
    } = useWorkspaceContext(),
    [q, setQ] = useState(""),
    // The move to another rule that waits for the person to decide about unsaved edits.
    [discard, setDiscard] = useState<null | (() => void)>(null);
  function leaving(move: () => void) {
    const saved = data.rules.find((r) => r.id === rule.id);
    // A blank draft holds nothing worth asking about.
    const edited = saved ? JSON.stringify(saved) !== JSON.stringify(rule) : Boolean(rule.name.trim() || rule.conditions.trim());
    if (edited) setDiscard(() => move);
    else move();
  }
  const rows = data.rules.filter(
    (r) =>
      r.name.toLowerCase().includes(q.toLowerCase()) &&
      (!sourceFilter || r.source === sourceFilter),
  );
  return (
    <Box
      title="Rules"
      icon={GitBranch}
      extra={
        <button
          onClick={() =>
            leaving(() => {
              setRule(newRule(account ? data : undefined));
              setCompactDetail(true);
            })
          }
        >
          <Plus />
          Draft rule
        </button>
      }
    >
      <Field label="Find a rule">
        <input
          type="search"
          placeholder="Search rules…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
      </Field>
      <Field label="Source">
        <select
          value={sourceFilter}
          onChange={(e) => setSourceFilter(e.target.value)}
        >
          <option value="">All sources</option>
          {data.sources.map((s) => (
            <option key={s.id}>{s.id}</option>
          ))}
        </select>
      </Field>
      <div className="record-list">
        {rows.map((r) => (
          <button
            key={r.id}
            aria-pressed={r.id === rule.id}
            onClick={() => {
              if (r.id === rule.id) return setCompactDetail(true);
              leaving(() => {
                setRule(r);
                setCompactDetail(true);
              });
            }}
          >
            <span>
              <strong>{r.name}</strong>
              <small>{r.source}</small>
            </span>
            {r.enabled === false && <span className="draft">Off</span>}
            <Badge severity={r.severity} />
          </button>
        ))}
      </div>
      {!rows.length && (
        <Empty
          title="No matching rules"
          action={
            <button
              onClick={() => {
                setQ("");
                setSourceFilter("");
              }}
            >
              Clear filters
            </button>
          }
        />
      )}
      {discard && (
        <Modal title="Discard unsaved changes?" onClose={() => setDiscard(null)}>
          <p>“{rule.name || "The draft rule"}” has edits that are not saved. Opening another rule discards them.</p>
          <div className="buttons">
            <button onClick={() => setDiscard(null)}>Keep editing</button>
            <button
              className="danger-btn"
              onClick={() => {
                discard();
                setDiscard(null);
              }}
            >
              Discard changes
            </button>
          </div>
        </Modal>
      )}
    </Box>
  );
}
