import { Modal } from "#components/modal.tsx";
import { type Group, useWorkspaceContext } from "#features/workspace";
import { plural } from "#lib/plural.ts";
import { Trash2 } from "lucide-react";
import { useState } from "react";

/** Deletes a recipient group after saying which rules lose it as a recipient. */
export function GroupDelete({ group }: { group: Group }) {
  const { data, commit, setGroupId } = useWorkspaceContext();
  const [confirming, setConfirming] = useState(false);
  const used = data.rules.filter((rule) => rule.targets.includes(group.name));
  return (
    <>
      <div className="buttons">
        <button className="danger-text" onClick={() => setConfirming(true)}>
          <Trash2 />
          Delete group
        </button>
      </div>
      {confirming && (
        <Modal title="Delete group?" onClose={() => setConfirming(false)}>
          <p>
            {used.length
              ? `${plural(used.length, "rule")} ${used.length === 1 ? "notifies" : "notify"} “${group.name}”: ${used.map((rule) => rule.name).join(", ")}. Remove the group from ${used.length === 1 ? "that rule" : "those rules"} first, or nobody will be notified through it.`
              : `No rule notifies “${group.name}”, so no notification is lost.`}
          </p>
          <div className="buttons">
            <button onClick={() => setConfirming(false)}>Cancel</button>
            <button
              className="danger-btn"
              onClick={() => {
                const groups = data.groups.filter((other) => other.id !== group.id);
                commit({ ...data, groups }, "Group deleted locally.");
                setGroupId(groups[0]?.id ?? "");
                setConfirming(false);
              }}
            >
              Delete group
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
