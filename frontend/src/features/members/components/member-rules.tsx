import { Box } from "#components/box.tsx";
import { Empty } from "#components/empty.tsx";
import { homeTeam, useWorkspaceContext } from "#features/workspace";
import { GitBranch } from "lucide-react";

export function AssignRules() {
  const { data, role, memberId, commit } = useWorkspaceContext(),
    m = data.members.find(
      (m) => m.id === memberId && (role === "Admin" || m.team === homeTeam),
    );
  return (
    <Box title="Assigned rules" icon={GitBranch}>
      {m && (role === "Admin" || m.role !== "Admin") ? (
        <>
          <p>{m.name}</p>
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
                              targets: e.target.checked
                                ? [...v.targets, m.name]
                                : v.targets.filter((t) => t !== m.name),
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
      ) : (
        <Empty title="Select an eligible member" />
      )}
    </Box>
  );
}
