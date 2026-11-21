import { Box } from "#components/box.tsx";
import { Empty } from "#components/empty.tsx";
import { Field } from "#components/field.tsx";
import { type Role, roles, useWorkspaceContext } from "#features/workspace";
import { unique } from "#lib/id.ts";
import { Check, UserPlus, Users } from "lucide-react";
import { useState } from "react";

export function AccountList() {
  const { data, setMemberId, navigate, setCompactDetail } = useWorkspaceContext(),
    [filter, setFilter] = useState("*");
  const members = data.members.filter(
    (m) => filter === "*" || m.team === filter,
  );
  return (
    <Box title="All accounts" icon={Users}>
      <Field label="Team">
        <select value={filter} onChange={(e) => setFilter(e.target.value)}>
          <option value="*">All teams</option>
          <option value="">Without a team</option>
          {data.teams.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
      </Field>
      <div className="record-list">
        {members.map((m) => (
          <button
            key={m.id}
            onClick={() => {
              setMemberId(m.id);
              navigate("Users");
              setCompactDetail(true);
            }}
          >
            <span>
              <strong>{m.name}</strong>
              <small>{m.email}</small>
            </span>
            <span>{m.role}</span>
          </button>
        ))}
      </div>
      {!members.length && <Empty title="No accounts in this team" />}
    </Box>
  );
}
export function CreateAccount() {
  const { data, commit } = useWorkspaceContext(),
    [error, setError] = useState("");
  return (
    <Box title="Create account" icon={UserPlus}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          const f = new FormData(e.currentTarget),
            email = String(f.get("email")).trim();
          if (
            data.members.some(
              (m) => m.email.toLowerCase() === email.toLowerCase(),
            )
          ) {
            setError("An account uses this email. Enter another address.");
            return;
          }
          const team = String(f.get("team"));
          commit(
            {
              ...data,
              members: [
                ...data.members,
                {
                  id: unique(),
                  name: String(f.get("name")).trim(),
                  email,
                  phone: "",
                  team,
                  role: team ? (String(f.get("role")) as Role) : "Pending",
                  duty: false,
                },
              ],
            },
            "Account created locally.",
          );
          e.currentTarget.reset();
          setError("");
        }}
      >
        <Field label="Full name">
          <input name="name" autoComplete="name" required />
        </Field>
        <Field label="Email">
          <input name="email" autoComplete="email" type="email" required />
        </Field>
        <Field label="Assign to team">
          <select name="team">
            <option value="">Without a team</option>
            {data.teams.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Role when assigned">
          <select name="role">
            {roles.map((r) => (
              <option key={r}>{r}</option>
            ))}
          </select>
        </Field>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <button className="primary">
          <Check />
          Create account
        </button>
      </form>
    </Box>
  );
}
