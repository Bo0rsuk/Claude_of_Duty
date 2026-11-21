import { Box } from "#components/box.tsx";
import { Empty } from "#components/empty.tsx";
import { Field } from "#components/field.tsx";
import { Modal } from "#components/modal.tsx";
import { Save } from "#components/save.tsx";
import { homeTeam, useWorkspaceContext } from "#features/workspace";
import { unique } from "#lib/id.ts";
import { Layers, Plus, Users } from "lucide-react";
import { useState } from "react";

export function GroupList() {
  const { data, groupId, setGroupId, setCompactDetail, commit } = useWorkspaceContext(),
    [create, setCreate] = useState(false),
    [name, setName] = useState("");
  return (
    <Box
      title="Recipient groups"
      icon={Layers}
      extra={
        <button onClick={() => setCreate(true)}>
          <Plus />
          New group
        </button>
      }
    >
      <div className="record-list">
        {data.groups.map((g) => (
          <button
            key={g.id}
            aria-pressed={g.id === groupId}
            onClick={() => {
              setGroupId(g.id);
              setCompactDetail(true);
            }}
          >
            <span>
              <strong>{g.name}</strong>
              <small>
                {g.members.length} members ·{" "}
                {data.rules.filter((r) => r.targets.includes(g.name)).length}{" "}
                rules
              </small>
            </span>
          </button>
        ))}
      </div>
      {create && (
        <Modal title="Create group" onClose={() => setCreate(false)}>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              const id = unique();
              commit(
                {
                  ...data,
                  groups: [
                    ...data.groups,
                    { id, name: name.trim(), members: [] },
                  ],
                },
                "Group created locally.",
              );
              setGroupId(id);
              setName("");
              setCreate(false);
            }}
          >
            <Field label="Group name">
              <input
                required
                pattern=".*\S.*"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </Field>
            <Save label="Create group" />
          </form>
        </Modal>
      )}
    </Box>
  );
}
export function GroupMembers() {
  const { groupId, data, commit } = useWorkspaceContext(),
    g = data.groups.find((g) => g.id === groupId);
  return (
    <Box title="Group members" icon={Users}>
      {g ? (
        <>
          <h3>{g.name}</h3>
          {data.members
            .filter((m) => m.team === homeTeam)
            .map((m) => (
              <label className="check-line person" key={m.id}>
                <input
                  type="checkbox"
                  checked={g.members.includes(m.id)}
                  onChange={(e) =>
                    commit(
                      {
                        ...data,
                        groups: data.groups.map((v) =>
                          v.id === g.id
                            ? {
                                ...v,
                                members: e.target.checked
                                  ? [...v.members, m.id]
                                  : v.members.filter((id) => id !== m.id),
                              }
                            : v,
                        ),
                      },
                      "Group membership updated locally.",
                    )
                  }
                />
                <span>
                  <strong>{m.name}</strong>
                  <small>
                    {m.email} · {m.phone}
                  </small>
                </span>
                {m.duty && <span className="healthy">On call</span>}
              </label>
            ))}
        </>
      ) : (
        <Empty title="Select a group" />
      )}
    </Box>
  );
}
