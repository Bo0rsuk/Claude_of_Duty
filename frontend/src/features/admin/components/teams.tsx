import { Box } from "#components/box.tsx";
import { Empty } from "#components/empty.tsx";
import { Field } from "#components/field.tsx";
import { Modal } from "#components/modal.tsx";
import { Save } from "#components/save.tsx";
import { useWorkspaceContext } from "#features/workspace";
import { unique } from "#lib/id.ts";
import { Plus, SlidersHorizontal, Users } from "lucide-react";
import { useState } from "react";

export function TeamList() {
  const { data, teamId, setTeamId, commit, setCompactDetail } = useWorkspaceContext(),
    [create, setCreate] = useState(false);
  return (
    <Box
      title="Teams"
      icon={Users}
      extra={
        <button onClick={() => setCreate(true)}>
          <Plus />
          Create team
        </button>
      }
    >
      <div className="record-list">
        {data.teams.map((t) => (
          <button
            key={t.id}
            aria-pressed={teamId === t.id}
            onClick={() => {
              setTeamId(t.id);
              setCompactDetail(true);
            }}
          >
            <span>
              <strong>{t.name}</strong>
              <small>
                {data.members.filter((m) => m.team === t.id).length} members
              </small>
            </span>
          </button>
        ))}
      </div>
      {create && (
        <Modal title="Create team" onClose={() => setCreate(false)}>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              const name = String(
                  new FormData(e.currentTarget).get("name"),
                ).trim(),
                id = unique();
              commit(
                {
                  ...data,
                  teams: [
                    ...data.teams,
                    {
                      id,
                      name,
                      lead: "",
                      code: "TEAM-" + unique().slice(0, 6).toUpperCase(),
                    },
                  ],
                },
                "Team created locally.",
              );
              setTeamId(id);
              setCreate(false);
            }}
          >
            <Field label="Team name">
              <input name="name" required pattern=".*\S.*" />
            </Field>
            <Save label="Create team" />
          </form>
        </Modal>
      )}
    </Box>
  );
}
export function ManageTeam() {
  const { data, teamId, commit } = useWorkspaceContext(),
    t = data.teams.find((t) => t.id === teamId);
  if (!t) return <Empty title="Choose a team" />;
  return (
    <Box title="Team settings" icon={SlidersHorizontal}>
      <form
        key={JSON.stringify([t, data.members.map((m) => m.team)])}
        onSubmit={(e) => {
          e.preventDefault();
          const f = new FormData(e.currentTarget),
            lead = String(f.get("lead")),
            ids = f.getAll("members");
          commit(
            {
              ...data,
              teams: data.teams.map((v) =>
                v.id === t.id ? { ...v, name: String(f.get("name")), lead } : v,
              ),
              members: data.members.map((m) =>
                m.id === lead
                  ? { ...m, team: t.id, role: "Team lead" }
                  : ids.includes(m.id)
                    ? { ...m, team: t.id }
                    : m.team === t.id
                      ? { ...m, team: "", role: "Pending" }
                      : m,
              ),
            },
            "Team settings saved locally.",
          );
        }}
      >
        <Field label="Team name">
          <input required name="name" defaultValue={t.name} />
        </Field>
        <Field label="Team lead">
          <select name="lead" defaultValue={t.lead}>
            <option value="">Unassigned</option>
            {data.members
              .filter((m) => m.role !== "Admin" && (m.team === t.id || !m.team))
              .map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name}
                </option>
              ))}
          </select>
        </Field>
        <fieldset>
          <legend>Team members</legend>
          {data.members
            .filter((m) => m.team === t.id || !m.team)
            .map((m) => (
              <label key={m.id} className="check-line">
                <input
                  type="checkbox"
                  name="members"
                  value={m.id}
                  defaultChecked={m.team === t.id}
                />
                {m.name}
              </label>
            ))}
        </fieldset>
        <Save label="Save team" />
      </form>
    </Box>
  );
}
