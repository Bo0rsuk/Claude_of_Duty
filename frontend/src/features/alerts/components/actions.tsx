import { Field } from "#components/field.tsx";
import { Modal } from "#components/modal.tsx";
import { addAction } from "#features/alerts/utils/add-action.ts";
import { type Alert, type Status, useWorkspaceContext } from "#features/workspace";
import { CheckCheck, CircleCheck, Ellipsis } from "lucide-react";
import { useState } from "react";

export function Actions() {
  const { alert: a, data, commit } = useWorkspaceContext(),
    [dialog, setDialog] = useState(""),
    [target, setTarget] = useState("");
  if (!a) return null;
  const update = (next: Alert, message: string) => {
    commit(
      {
        ...data,
        alerts: data.alerts.map((x) => (x.id === next.id ? next : x)),
      },
      message,
    );
    setDialog("");
  };
  function progress(status: Status) {
    if (a)
      update(
        { ...addAction(a, status, "Updated in the demo workspace"), status },
        "Alert " + status.toLowerCase() + " locally.",
      );
  }
  return (
    <div className="alert-actions">
      {a.status === "Open" ? (
        <button className="primary" onClick={() => progress("Acknowledged")}>
          <CheckCheck />
          Acknowledge alert
        </button>
      ) : a.status === "Acknowledged" ? (
        <button className="primary" onClick={() => progress("Resolved")}>
          <CircleCheck />
          Resolve alert
        </button>
      ) : (
        <span className="resolved-text">
          <CircleCheck />
          Resolved
        </span>
      )}
      <button
        aria-label="More alert actions"
        onClick={() => {
          setTarget(a.target);
          setDialog("Manage alert");
        }}
      >
        <Ellipsis />
      </button>
      {dialog && (
        <Modal title={dialog} onClose={() => setDialog("")}>
          {dialog === "Manage alert" ? (
            <div className="menu-list">
              {a.status === "Open" && (
                <button onClick={() => progress("Resolved")}>
                  Resolve alert
                </button>
              )}
              <button onClick={() => setDialog("Reassign alert")}>
                Reassign alert
              </button>
              <button
                onClick={() =>
                  update(
                    addAction(
                      a,
                      "Sent",
                      "Simulated resend via " + a.channels.join(" and "),
                    ),
                    "Notification resend simulated.",
                  )
                }
              >
                Resend notification
              </button>
              <button
                className="danger-text"
                onClick={() => setDialog("Delete alert")}
              >
                Delete alert
              </button>
            </div>
          ) : dialog === "Reassign alert" ? (
            <>
              <Field label="Recipient">
                <select
                  value={target}
                  onChange={(e) => setTarget(e.target.value)}
                >
                  {[
                    ...new Set([
                      a.target,
                      "On-call engineer",
                      ...data.members
                        .filter((m) => m.team === "platform")
                        .map((m) => m.name),
                      ...data.groups.map((g) => g.name),
                    ]),
                  ].map((v) => (
                    <option key={v}>{v}</option>
                  ))}
                </select>
              </Field>
              <button
                className="primary"
                onClick={() =>
                  update(
                    { ...addAction(a, "Reassigned", target), target },
                    "Alert reassigned to " + target + ".",
                  )
                }
              >
                Reassign alert
              </button>
            </>
          ) : (
            <>
              <p>Delete {a.id} and its timeline from this demo workspace?</p>
              <div className="buttons">
                <button onClick={() => setDialog("")}>Cancel</button>
                <button
                  className="danger-btn"
                  onClick={() => {
                    commit(
                      {
                        ...data,
                        alerts: data.alerts.filter((x) => x.id !== a.id),
                      },
                      "Alert deleted locally.",
                    );
                    setDialog("");
                  }}
                >
                  Delete alert
                </button>
              </div>
            </>
          )}
        </Modal>
      )}
    </div>
  );
}
