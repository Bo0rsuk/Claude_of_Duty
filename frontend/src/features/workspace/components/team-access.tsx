import { Empty } from "#components/empty.tsx";
import { Field } from "#components/field.tsx";
import { useWorkspaceContext } from "#features/workspace/context/workspace-context.tsx";
import { useState } from "react";

export function TeamAccess({ access, changeAccess }: {
  access: string;
  changeAccess: (access: string) => void;
}) {
  const { data, commit, setPrototype, account } = useWorkspaceContext();
  const [join, setJoin] = useState("");
  const [error, setError] = useState("");
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  if (account) {
    const run = (action: Promise<void>) => {
      setBusy(true);
      setError("");
      action.catch((failure: Error) => setError(failure.message)).finally(() => setBusy(false));
    };
    return (
      <section className="surface join">
        <h2>Join your team</h2>
        <p>Enter the code your admin shared, or start a new team and invite others.</p>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            run(account.join(join.trim()));
          }}
        >
          <Field label="Team code">
            <input
              required
              autoComplete="off"
              spellCheck={false}
              value={join}
              onChange={(e) => setJoin(e.target.value)}
              placeholder="ABCD-EFGH"
            />
          </Field>
          <button className="primary" disabled={busy}>
            Join team
          </button>
        </form>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            run(account.create(name.trim()));
          }}
        >
          <Field label="New team name">
            <input required pattern=".*\S.*" value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
          <button disabled={busy}>Create a team</button>
        </form>
        {error && (
          <p role="alert" className="error">
            {error}
          </p>
        )}
        <div className="buttons">
          <button onClick={() => account.signOut()}>Sign out</button>
        </div>
      </section>
    );
  }
  if (access === "Signed out")
    return (
      <section className="surface">
        <Empty
          title="Signed out of the demo"
          action={
            <button
              className="primary"
              onClick={() => changeAccess("Assigned")}
            >
              Enter demo workspace
            </button>
          }
        />
      </section>
    );
  return (
    <section className="surface join">
      <h2>
        {access === "Waiting" ? "Waiting for a role" : "Join your team"}
      </h2>
      <p>
        {access === "Waiting"
          ? "Your join request is saved. A team lead or Admin can assign your role in Users."
          : "Your account needs a team and an assigned role to access the alert workspace."}
      </p>
      {access !== "Waiting" && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            const t = data.teams.find(
              (t) => t.code === join.trim().toUpperCase(),
            );
            if (!t) {
              setError(
                "That code did not match a team. Check the code and try again.",
              );
              return;
            }
            commit(
              {
                ...data,
                members: data.members.map((m) =>
                  m.id === "guest"
                    ? { ...m, team: t.id, role: "Pending" }
                    : m,
                ),
              },
              "Join request saved locally.",
            );
            changeAccess("Waiting");
          }}
        >
          <Field label="Team code">
            <input
              required
              autoComplete="off"
              spellCheck={false}
              aria-invalid={!!error}
              aria-describedby={error ? "join-error" : undefined}
              value={join}
              onChange={(e) => setJoin(e.target.value)}
              placeholder="Enter team code…"
            />
          </Field>
          {error && (
            <p id="join-error" role="alert" className="error">
              {error}
            </p>
          )}
          <button className="primary">Request to join</button>
        </form>
      )}
      <div className="buttons">
        <button onClick={() => setPrototype(true)}>Switch demo role</button>
        <button onClick={() => changeAccess("Signed out")}>Sign out</button>
      </div>
    </section>
  );
}
