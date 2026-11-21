import { Badge } from "#components/badge.tsx";
import { Box } from "#components/box.tsx";
import { Empty } from "#components/empty.tsx";
import { Field } from "#components/field.tsx";
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
    } = useWorkspaceContext(),
    [q, setQ] = useState("");
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
          onClick={() => {
            setRule(newRule());
            setCompactDetail(true);
          }}
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
              setRule(r);
              setCompactDetail(true);
            }}
          >
            <span>
              <strong>{r.name}</strong>
              <small>{r.source}</small>
            </span>
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
    </Box>
  );
}
