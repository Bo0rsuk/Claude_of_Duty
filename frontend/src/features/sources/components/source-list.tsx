import { Box } from "#components/box.tsx";
import { Empty } from "#components/empty.tsx";
import { Field } from "#components/field.tsx";
import { SourceWizard } from "#features/sources/components/source-wizard.tsx";
import { useWorkspaceContext } from "#features/workspace";
import { relative } from "#lib/time.ts";
import { Database, Plus } from "lucide-react";
import { useState } from "react";

export function SourceList() {
  const { data, sourceId, setSourceId, setCompactDetail } = useWorkspaceContext(),
    [wizard, setWizard] = useState(false),
    [q, setQ] = useState("");
  const rows = data.sources.filter((s) => s.id.includes(q.toLowerCase()));
  return (
    <Box
      title="Event sources"
      icon={Database}
      extra={
        <button onClick={() => setWizard(true)}>
          <Plus />
          Add source
        </button>
      }
    >
      <Field label="Find a source">
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search sources…"
        />
      </Field>
      <div className="record-list">
        {rows.map((s) => (
          <button
            key={s.id}
            aria-pressed={s.id === sourceId}
            onClick={() => {
              setSourceId(s.id);
              setCompactDetail(true);
            }}
          >
            <span>
              <strong>{s.id}</strong>
              <small>
                {s.type} · {s.format} ·{" "}
                {data.rules.filter((r) => r.source === s.id).length} rules
              </small>
              <small>Last event {relative(s.last)}</small>
            </span>
            <span className="healthy">Receiving</span>
          </button>
        ))}
      </div>
      {!rows.length && (
        <Empty
          title="No matching sources"
          action={<button onClick={() => setQ("")}>Clear search</button>}
        />
      )}
      {wizard && <SourceWizard onClose={() => setWizard(false)} />}
    </Box>
  );
}
