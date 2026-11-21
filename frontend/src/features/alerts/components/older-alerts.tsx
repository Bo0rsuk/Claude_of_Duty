import { useWorkspaceContext } from "#features/workspace";
import { plural } from "#lib/plural.ts";
import { History } from "lucide-react";
import { useState } from "react";

/** Fetches the next page of alerts older than the ones in the feed. Shown only while the server holds more. */
export function OlderAlerts() {
  const { account, data, notify } = useWorkspaceContext();
  const [busy, setBusy] = useState(false);
  if (!account?.hasOlderAlerts) return null;
  return (
    <div className="older-alerts">
      <span>The {plural(data.alerts.length, "most recent alert")} loaded.</span>
      <button
        disabled={busy}
        onClick={() => {
          setBusy(true);
          account
            .loadOlderAlerts()
            .then((count) => notify(count ? plural(count, "older alert") + " loaded." : "There are no older alerts."))
            .catch((failure: Error) => notify("Older alerts could not be loaded. " + failure.message + " Try again."))
            .finally(() => setBusy(false));
        }}
      >
        <History />
        {busy ? "Loading older alerts…" : "Load older alerts"}
      </button>
    </div>
  );
}
