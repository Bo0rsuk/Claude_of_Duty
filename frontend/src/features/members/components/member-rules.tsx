import { Box } from "#components/box.tsx";
import { Empty } from "#components/empty.tsx";
import { type Member, homeTeam, useWorkspaceContext } from "#features/workspace";
import { GitBranch } from "lucide-react";

/** The rules that notify this person, as a list to tick. */
export function MemberRules({ member: m }: { member: Member }) {
  const { data, commit } = useWorkspaceContext();
  if (!data.rules.length) return <p className="hint">There are no rules yet.</p>;
  return (
    <>
      {data.rules.map((r) => (
        <label key={r.id} className="check-line">
          <input
            type="checkbox"
            checked={r.targets.includes(m.name)}
            onChange={(e) =>
              commit(
                {
                  ...data,
                  rules: data.rules.map((v) =>
                    v.id === r.id
                      ? {
                          ...v,
                          targets: e.target.checked ? [...v.targets, m.name] : v.targets.filter((t) => t !== m.name),
                        }
                      : v,
                  ),
                },
                "Rule assignment saved locally.",
              )
            }
          />
          {r.name}
        </label>
      ))}
    </>
  );
}

export function AssignRules() {
  const { data, role, memberId } = useWorkspaceContext(),
    m = data.members.find((m) => m.id === memberId && (role === "Admin" || m.team === homeTeam));
  return (
    <Box title="Assigned rules" icon={GitBranch}>
      {m && (role === "Admin" || m.role !== "Admin") ? (
        <>
          <p>{m.name}</p>
          <MemberRules member={m} />
        </>
      ) : (
        <Empty title="Select an eligible member" />
      )}
    </Box>
  );
}
