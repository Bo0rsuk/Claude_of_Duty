import { Badge } from "#components/badge.tsx";
import { Modal } from "#components/modal.tsx";
import { type Severity, useWorkspaceContext } from "#features/workspace";
import { BellRing } from "lucide-react";

const severityLabel = (severity: string) => (severity.charAt(0) + severity.slice(1).toLowerCase()) as Severity;

/**
 * An in-app notification: a dialog in the middle of the page that dims
 * everything else, because it was sent to this person and waits for them.
 */
export function IncomingAlert() {
  const { incoming, openIncoming, dismissIncoming } = useWorkspaceContext();
  if (!incoming) return null;
  return (
    <Modal title="New alert for you" onClose={dismissIncoming}>
      <div className="incoming-alert">
        <Badge severity={severityLabel(incoming.severity)} />
        <h3>{incoming.title}</h3>
        {incoming.body && <p>{incoming.body}</p>}
        <div className="buttons">
          <button className="primary" onClick={openIncoming}>
            <BellRing />
            Open alert
          </button>
          <button onClick={dismissIncoming}>Dismiss</button>
        </div>
      </div>
    </Modal>
  );
}
