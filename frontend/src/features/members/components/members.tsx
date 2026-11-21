import { PHONE_HELP, normalizePhone } from "#lib/phone.ts";
import { Box } from "#components/box.tsx";
import { Empty } from "#components/empty.tsx";
import { Field } from "#components/field.tsx";
import { Save } from "#components/save.tsx";
import { type Member, homeTeam, roles, useWorkspaceContext } from "#features/workspace";
import { SlidersHorizontal, Users } from "lucide-react";
import { useState } from "react";

export function UserList() {
  const { data, role, memberId, setMemberId, setCompactDetail } = useWorkspaceContext(),
    [q, setQ] = useState("");
  const rows = data.members.filter(
    (m) =>
      (role === "Admin" || m.team === homeTeam) &&
      (m.name + m.email).toLowerCase().includes(q.toLowerCase()),
  );
  return (
    <Box title="Team members" icon={Users}>
      <Field label="Find a member">
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Name or email…"
        />
      </Field>
      <div className="record-list">
        {rows.map((m) => (
          <button
            key={m.id}
            aria-pressed={memberId === m.id}
            onClick={() => {
              setMemberId(m.id);
              setCompactDetail(true);
            }}
          >
            <span>
              <strong>{m.name}</strong>
              <small>{m.email}</small>
            </span>
            <span className="neutral-tag">{m.role}</span>
          </button>
        ))}
      </div>
      {!rows.length && (
        <Empty
          title="No matching members"
          action={<button onClick={() => setQ("")}>Clear search</button>}
        />
      )}
    </Box>
  );
}
export function ManageUser() {
  const { data, role, memberId, commit, account } = useWorkspaceContext(),
    m = data.members.find(
      (m) => m.id === memberId && (role === "Admin" || m.team === homeTeam),
    );
  const locked = role !== "Admin" && m?.role === "Admin";
  return (
    <Box title="Member details" icon={SlidersHorizontal}>
      {m ? (
        <form
          key={JSON.stringify(m)}
          onSubmit={(e) => {
            e.preventDefault();
            if (locked) return;
            const f = new FormData(e.currentTarget),
              name = String(f.get("name")),
              phone = normalizePhone(String(f.get("phone")));
            if (phone === null) {
              // The browser shows the reason at the field, like its own checks do.
              const field = e.currentTarget.elements.namedItem("phone") as HTMLInputElement;
              field.setCustomValidity(PHONE_HELP);
              field.reportValidity();
              return;
            }
            const next = {
                ...m,
                name,
                email: String(f.get("email")),
                phone,
                role: String(f.get("role")) as Member["role"],
              };
            commit(
              {
                ...data,
                members: data.members.map((v) => (v.id === m.id ? next : v)),
                rules: data.rules.map((r) => ({
                  ...r,
                  targets: r.targets.map((t) => (t === m.name ? name : t)),
                })),
              },
              "Member saved locally.",
            );
          }}
        >
          <fieldset disabled={locked}>
            <Field label="Full name">
              <input name="name" required defaultValue={m.name} />
            </Field>
            <Field label="Email">
              <input
                type="email"
                name="email"
                required
                defaultValue={m.email}
              />
            </Field>
            <Field label="Phone">
              <input
                type="tel"
                name="phone"
                defaultValue={m.phone}
                placeholder="+48 600 100 200"
                // Checked on save; typing again clears the message.
                onInput={(e) => e.currentTarget.setCustomValidity("")}
              />
            </Field>
            {!locked && (
              <Field label="Assigned role">
                <select name="role" defaultValue={m.role}>
                  {[
                    // "Waiting for a role" exists in the demo only.
                    ...(account ? [] : ["Pending"]),
                    ...roles.filter((r) => role === "Admin" || r !== "Admin"),
                  ].map((r) => (
                    <option key={r}>{r}</option>
                  ))}
                </select>
              </Field>
            )}
          </fieldset>
          {!locked && <Save label="Save member" />}
          {locked && <p>Admins manage this account.</p>}
        </form>
      ) : (
        <Empty title="Choose a team member" />
      )}
    </Box>
  );
}
