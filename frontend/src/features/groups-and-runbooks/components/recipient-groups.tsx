import { plural } from "#lib/plural.ts";
import { Box } from "#components/box.tsx";
import { CloseDetail } from "#components/close-detail.tsx";
import { Empty } from "#components/empty.tsx";
import { Field } from "#components/field.tsx";
import { Modal } from "#components/modal.tsx";
import { Save } from "#components/save.tsx";
import { GroupContacts } from "#features/groups-and-runbooks/components/group-contacts.tsx";
import { GroupDelete } from "#features/groups-and-runbooks/components/group-delete.tsx";
import { StatusTag } from "#components/status-tag.tsx";
import { homeTeam, useWorkspaceContext } from "#features/workspace";
import { unique } from "#lib/id.ts";
import { Layers, Plus, Users, X } from "lucide-react";
import { useState } from "react";

export function GroupList() {
  const { data, groupId, setGroupId, setCompactDetail, compactDetail, panel, commit } = useWorkspaceContext(),
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
            // With the details closed no group is "open", so none is marked.
            aria-pressed={g.id === groupId && (panel !== "Groups" || compactDetail)}
            onClick={() => {
              setGroupId(g.id);
              setCompactDetail(true);
            }}
          >
            <span>
              <strong>{g.name}</strong>
              <small>
                {plural(g.members.length + (g.contacts?.length ?? 0), "member")} ·{" "}
                {plural(data.rules.filter((r) => r.targets.includes(g.name)).length, "rule")}
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
              setCompactDetail(true);
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
    g = data.groups.find((g) => g.id === groupId),
    [entry, setEntry] = useState(""),
    [error, setError] = useState("");
  const people = data.members.filter((m) => m.team === homeTeam);
  const inGroup = g ? people.filter((m) => g.members.includes(m.id)) : [];
  const setMembers = (members: string[], message: string) =>
    g && commit({ ...data, groups: data.groups.map((v) => (v.id === g.id ? { ...v, members } : v)) }, message);
  // People not in the group yet, and other groups whose members can be brought in at once.
  const suggestions = g
    ? [
        ...people.filter((m) => !g.members.includes(m.id)).map((m) => m.name),
        ...data.groups.filter((other) => other.id !== g.id && other.members.length).map((other) => other.name),
      ]
    : [];
  function add() {
    if (!g) return;
    const text = entry.trim().toLowerCase();
    if (!text) return;
    const person = people.find((m) => m.name.toLowerCase() === text || m.email.toLowerCase() === text);
    const group = data.groups.find((other) => other.id !== g.id && other.name.toLowerCase() === text);
    if (!person && !group) {
      setError("Choose a person or a group from the list.");
      return;
    }
    const added = person ? [person.id] : group!.members;
    const members = [...new Set([...g.members, ...added])];
    if (members.length === g.members.length) {
      setError(person ? person.name + " is already in this group." : "Everyone from " + group!.name + " is already in this group.");
      return;
    }
    setMembers(
      members,
      person ? "Group membership updated locally." : "Members of " + group!.name + " added locally.",
    );
    setEntry("");
    setError("");
  }
  return (
    <Box title={g ? g.name : "Group members"} icon={Users} extra={<CloseDetail page="Groups" label="Close group" />}>
      {g ? (
        <>
          <div className="add-recipient">
            <input
              aria-label="Add a person or a group"
              list="group-member-suggestions"
              value={entry}
              placeholder="Add a person, or everyone from another group…"
              onChange={(e) => {
                setEntry(e.target.value);
                setError("");
              }}
              onKeyDown={(e) => {
                if (e.key !== "Enter") return;
                e.preventDefault();
                add();
              }}
            />
            <datalist id="group-member-suggestions">
              {suggestions.map((name) => (
                <option key={name} value={name} />
              ))}
            </datalist>
            <button type="button" aria-label="Add to group" title="Add to group" disabled={!entry.trim()} onClick={add}>
              <Plus />
            </button>
          </div>
          {error && (
            <p role="alert" className="error">
              {error}
            </p>
          )}
          {!inGroup.length && !g.contacts?.length && <p className="hint">Nobody is in this group yet.</p>}
          {inGroup.map((m) => (
            <div className="check-line person" key={m.id}>
              <span>
                <strong>{m.name}</strong>
                <small>{[m.email, m.phone].filter(Boolean).join(" · ")}</small>
              </span>
              {m.duty && <StatusTag value="On call" />}
              <button
                type="button"
                className="icon-btn"
                aria-label={"Remove " + m.name + " from the group"}
                title="Remove from group"
                onClick={() =>
                  setMembers(
                    g.members.filter((id) => id !== m.id),
                    "Group membership updated locally.",
                  )
                }
              >
                <X />
              </button>
            </div>
          ))}
          <GroupContacts group={g} />
          <GroupDelete group={g} />
        </>
      ) : (
        <Empty title="Select a group" />
      )}
    </Box>
  );
}
