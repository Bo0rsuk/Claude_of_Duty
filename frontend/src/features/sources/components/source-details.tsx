import { plural } from "#lib/plural.ts";
import { Box } from "#components/box.tsx";
import { Empty } from "#components/empty.tsx";
import { SourceActions } from "#features/sources/components/source-actions.tsx";
import { useWorkspaceContext } from "#features/workspace";
import { relative } from "#lib/time.ts";
import { FileCode2, GitBranch } from "lucide-react";

export function SourceDetails() {
  const { data, sourceId, setSourceFilter, navigate, account } = useWorkspaceContext(),
    s = data.sources.find((v) => v.id === sourceId);
  return (
    <Box title="Source details" icon={FileCode2}>
      {s ? (
        <>
          <h3>{s.id}</h3>
          <dl className="plain-dl">
            <dt>Type</dt>
            <dd>{s.type}</dd>
            <dt>Format</dt>
            <dd>{s.format}</dd>
            <dt>{account ? (s.type === "Webhook" ? "Send events to" : "Connection") : "Demo connection"}</dt>
            <dd>{s.connection}</dd>
            <dt>Last received</dt>
            <dd>{relative(s.last)}</dd>
          </dl>
          <details>
            <summary>Field mapping</summary>
            <pre>{s.mapping}</pre>
          </details>
          <button
            onClick={() => {
              setSourceFilter(s.id);
              navigate("Rules");
            }}
          >
            <GitBranch />
            View {plural(data.rules.filter((r) => r.source === s.id).length, "linked rule")}
          </button>
          <SourceActions key={s.ref ?? s.id} source={s} />
        </>
      ) : (
        <Empty title="Choose a source" />
      )}
    </Box>
  );
}
