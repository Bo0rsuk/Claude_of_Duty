import { CriticalSound } from "#features/workspace/components/critical-sound.tsx";
import { Field } from "#components/field.tsx";
import { Modal } from "#components/modal.tsx";
import { useWorkspaceContext } from "#features/workspace/context/workspace-context.tsx";
import { roles } from "#features/workspace/domain/panel-access.ts";
import { homeTeam } from "#features/workspace/domain/workspace-defaults.ts";
import type { Role } from "#features/workspace/types/records.ts";
import { PushDevices } from "#features/workspace/components/push-devices.tsx";
import { LogOut, Pause, Play, Settings2 } from "lucide-react";

export function AccountMenu({ user, access, changeAccess, switchRole, closeAccount }: {
  user: string;
  access: string;
  changeAccess: (value: string) => void;
  switchRole: (role: Role) => void;
  closeAccount: () => void;
}) {
  const { data, role, paused, setPaused, mode, setMode, prototype, setPrototype, account } = useWorkspaceContext();
  if (account) {
    return (
      <Modal title="User menu" onClose={closeAccount}>
        <div className="user-summary">
          <span className="avatar">
            {user
              .split(" ")
              .map((v) => v[0])
              .join("")}
          </span>
          <span>
            <strong>{user}</strong>
            <small>
              {account.team} · {role} · {account.connected ? "Live" : "Reconnecting…"}
            </small>
          </span>
        </div>
        <PushDevices />
        <CriticalSound />
        <button
          onClick={() => {
            closeAccount();
            account.signOut();
          }}
        >
          <LogOut />
          Sign out
        </button>
      </Modal>
    );
  }
  const team =
      data.teams.find((t) => t.id === homeTeam)?.name ?? "Platform operations",
    guest = data.members.find((m) => m.id === "guest");
  return (
    <Modal
      title={prototype ? "Prototype controls" : "User menu"}
      onClose={() => {
        setPrototype(false);
        closeAccount();
      }}
    >
      <div className="user-summary">
        <span className="avatar">
          {user
            .split(" ")
            .map((v) => v[0])
            .join("")}
        </span>
        <span>
          <strong>{user}</strong>
          <small>
            {team} · {paused ? "Live paused" : "Live demo"}
          </small>
        </span>
      </div>
      <Field label="Demo role">
        <select
          value={role}
          onChange={(e) => switchRole(e.target.value as Role)}
        >
          {roles.map((r) => (
            <option key={r}>{r}</option>
          ))}
        </select>
      </Field>
      {prototype ? (
        <>
          <Field label="Workspace state">
            <select
              value={mode}
              onChange={(e) => {
                setMode(e.target.value);
                setPrototype(false);
              }}
            >
              {[
                "Normal",
                "Loading",
                "Empty",
                "Error",
                "Partial data",
                "Permission denied",
              ].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          </Field>
          <Field label="Team access">
            <select
              value={access}
              onChange={(e) => changeAccess(e.target.value)}
            >
              <option value="Assigned">Assigned to team</option>
              <option value="Unassigned">Without a team</option>
              <option value="Waiting">Waiting for role</option>
            </select>
          </Field>
          <p className="helper">
            Demo join code <code>{data.teams[0]?.code}</code>
            {guest?.team && " · Riley has a saved membership request."}
          </p>
          <button onClick={() => setPaused(!paused)}>
            {paused ? <Play /> : <Pause />}
            {paused ? "Resume demo feed" : "Pause demo feed"}
          </button>
        </>
      ) : (
        <button
          onClick={() => {
            closeAccount();
            setPrototype(true);
          }}
        >
          <Settings2 />
          Open prototype controls
        </button>
      )}
    </Modal>
  );
}
