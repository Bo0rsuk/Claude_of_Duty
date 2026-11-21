import { useWorkspaceContext } from "#features/workspace/context/workspace-context.tsx";
import type * as Api from "#services/backend/api-types.ts";
import { forgetDevice, loadDevices } from "#services/backend/gateway.ts";
import { api } from "#services/backend/http.ts";
import { disablePush, enablePush, pushSupported } from "#services/backend/push.ts";
import { BellOff, BellRing, X } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

/** The browser family a push address belongs to. The server stores nothing else about a device. */
function deviceName(endpoint: string) {
  const host = (() => {
    try {
      return new URL(endpoint).hostname;
    } catch {
      return "";
    }
  })();
  if (host.endsWith("googleapis.com")) return "Chrome, Edge or an Android browser";
  if (host.endsWith("mozilla.com")) return "Firefox";
  if (host.endsWith("apple.com")) return "Safari, iPhone or iPad";
  if (host.endsWith("windows.com")) return "Edge on Windows";
  return host || "Unknown browser";
}

const added = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" });

/** The address this browser receives push on, or null when push is off here. */
async function currentEndpoint() {
  if (!pushSupported()) return null;
  const registration = await navigator.serviceWorker.getRegistration();
  return (await registration?.pushManager.getSubscription())?.endpoint ?? null;
}

/** The signed-in person's devices that receive push notifications, with the means to add and remove them. */
export function PushDevices() {
  const { notify } = useWorkspaceContext();
  const [devices, setDevices] = useState<Api.PushSubscriptionRecord[] | null>(null),
    [here, setHere] = useState<string | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [loaded, endpoint] = await Promise.all([loadDevices(), currentEndpoint().catch(() => null)]);
      setDevices(loaded);
      setHere(endpoint);
      setError("");
    } catch (failure) {
      setError("The list of devices could not be loaded. " + (failure as Error).message);
    }
  }, []);
  useEffect(() => {
    load();
  }, [load]);

  /** Runs one change, then shows the list as the server has it. */
  function run(action: () => Promise<string>) {
    setBusy(true);
    action()
      .then(notify)
      .catch((failure: Error) => notify("Notifications were not changed. " + failure.message))
      .then(load)
      .finally(() => setBusy(false));
  }
  const enable = () =>
    run(async () =>
      (await enablePush(api))
        ? "Notifications are on for this device."
        : "This browser did not allow notifications. On iPhone, add the site to the home screen first.",
    );
  const disableHere = () =>
    run(async () => {
      await disablePush(api);
      return "Notifications are off for this device.";
    });
  const remove = (device: Api.PushSubscriptionRecord) =>
    device.endpoint === here
      ? disableHere()
      : run(async () => {
          await forgetDevice(device.subscriptionId);
          return "The device no longer receives notifications.";
        });
  const registeredHere = Boolean(here && devices?.some((device) => device.endpoint === here));

  return (
    <section className="push-devices" aria-label="Push notifications">
      <h3>Push notifications</h3>
      {error ? (
        <p role="alert" className="error">
          {error}{" "}
          <button type="button" onClick={load}>
            Try again
          </button>
        </p>
      ) : devices === null ? (
        <p className="hint">Loading your devices…</p>
      ) : devices.length ? (
        <ul>
          {devices.map((device) => (
            <li key={device.subscriptionId}>
              <span>
                <strong>{deviceName(device.endpoint)}</strong>
                <small>
                  {device.endpoint === here ? "This device · " : ""}Added {added.format(Date.parse(device.createdAt))}
                </small>
              </span>
              <button
                type="button"
                className="icon-btn"
                disabled={busy}
                aria-label={"Stop notifications on " + deviceName(device.endpoint)}
                title="Stop notifications on this device"
                onClick={() => remove(device)}
              >
                <X />
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="hint">
          No device receives push notifications yet. Enable them here to be alerted even when the application is closed.
        </p>
      )}
      {registeredHere ? (
        <button type="button" disabled={busy} onClick={disableHere}>
          <BellOff />
          Turn off notifications on this device
        </button>
      ) : (
        <button type="button" disabled={busy} onClick={enable}>
          <BellRing />
          Enable notifications on this device
        </button>
      )}
    </section>
  );
}
