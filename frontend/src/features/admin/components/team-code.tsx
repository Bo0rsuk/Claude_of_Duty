import { Box } from "#components/box.tsx";
import { Modal } from "#components/modal.tsx";
import { useWorkspaceContext } from "#features/workspace";
import { unique } from "#lib/id.ts";
import { Clipboard, KeyRound } from "lucide-react";
import { useState } from "react";

export function TeamCode() {
  const { data, teamId, commit, notify } = useWorkspaceContext(),
    [confirm, setConfirm] = useState(false),
    t = data.teams.find((t) => t.id === teamId);
  return (
    <Box title="Team code" icon={KeyRound}>
      {t && (
        <>
          <p>{t.name}</p>
          <code className="team-code">{t.code}</code>
          <div className="buttons">
            <button
              onClick={async () => {
                try {
                  await navigator.clipboard.writeText(t.code);
                  notify("Team code copied.");
                } catch {
                  notify("Select the displayed code and copy it manually.");
                }
              }}
            >
              <Clipboard />
              Copy code
            </button>
            <button onClick={() => setConfirm(true)}>Generate new code</button>
          </div>
          {confirm && (
            <Modal title="Replace team code?" onClose={() => setConfirm(false)}>
              <p>The current code will stop accepting join requests.</p>
              <div className="buttons">
                <button onClick={() => setConfirm(false)}>Cancel</button>
                <button
                  className="primary"
                  onClick={() => {
                    commit(
                      {
                        ...data,
                        teams: data.teams.map((v) =>
                          v.id === t.id
                            ? {
                                ...v,
                                code:
                                  "TEAM-" + unique().slice(0, 6).toUpperCase(),
                              }
                            : v,
                        ),
                      },
                      "New team code generated.",
                    );
                    setConfirm(false);
                  }}
                >
                  Generate new code
                </button>
              </div>
            </Modal>
          )}
        </>
      )}
    </Box>
  );
}
