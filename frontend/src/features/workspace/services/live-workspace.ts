// The workspace backed by the real API: loads it, keeps it current from the
// live feed and saves what the screens change.

import type { Data } from "#features/workspace/types/records.ts";
import { useLocal } from "#hooks/use-local.ts";
import type * as Api from "#services/backend/api-types.ts";
import { backend } from "#services/backend/config.ts";
import {
  type Created,
  applyChanges,
  loadAlertDetails,
  loadOlderAlerts,
  loadProfile,
  loadSnapshot,
  loadTimeline,
} from "#services/backend/gateway.ts";
import { api } from "#services/backend/http.ts";
import { subscribe } from "#services/backend/live-feed.ts";
import { type Change, planChanges } from "#services/backend/plan-changes.ts";
import { idToken, refreshSession } from "#services/backend/session.ts";
import { type Details, type Extras, type Snapshot, type Timelines, noExtras } from "#services/backend/snapshot.ts";
import { toWorkspace } from "#services/backend/to-workspace.ts";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

const EMPTY: Data = { alerts: [], sources: [], rules: [], members: [], teams: [], books: [], groups: [] };

export type LiveStatus = "loading" | "ready" | "no-team" | "error";
export type CommitResult = { data: Data; changes: Change[]; created: Created; notes: string[] };

function extrasOf(data: Data, previous: Extras): Extras {
  return {
    checks: { ...previous.checks, ...Object.fromEntries(data.alerts.map((alert) => [alert.id, alert.checks])) },
    windows: {
      ...previous.windows,
      ...Object.fromEntries(data.rules.map((rule) => [rule.id, { window: rule.window, fallback: rule.fallback }])),
    },
  };
}

export function useLiveWorkspace() {
  const [status, setStatus] = useState<LiveStatus>("loading");
  const [profile, setProfile] = useState<Api.Profile | null>(null);
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [timelines, setTimelines] = useState<Timelines>({});
  const [details, setDetails] = useState<Details>({});
  // Alerts older than the first page, loaded on request. They survive a reload of the first page.
  const [older, setOlder] = useState<{ items: Api.Alert[]; next: string | null } | null>(null);
  const [extras, setExtras] = useLocal<Extras>("fans:extras", noExtras);
  // What the screens show while a change is being saved.
  const [pending, setPending] = useState<Data | null>(null);
  const [connected, setConnected] = useState(false);
  const [popup, setPopup] = useState<Api.NotificationMessage | null>(null);
  const opened = useRef(new Set<string>());

  const reload = useCallback(async (): Promise<Snapshot | null> => {
    let current = await loadProfile();
    if (current.tokenRefreshNeeded) {
      // The live feed admits a subscriber only with a token that names their organization.
      await refreshSession();
      current = await loadProfile();
    }
    setProfile(current);
    if (!current.org) {
      setSnapshot(null);
      setStatus("no-team");
      return null;
    }
    const loaded = await loadSnapshot(current);
    setSnapshot(loaded);
    setStatus("ready");
    return loaded;
  }, []);

  const retry = useCallback(() => {
    setStatus("loading");
    reload().catch(() => setStatus("error"));
  }, [reload]);
  useEffect(retry, [retry]);

  const refreshTimeline = useCallback(async (alertId: string) => {
    const entries = await loadTimeline(alertId);
    setTimelines((current) => ({ ...current, [alertId]: entries }));
  }, []);

  /** Loads the history and the first event of an alert the user opened. */
  const openAlert = useCallback(
    (alertId: string) => {
      refreshTimeline(alertId).catch(() => undefined);
      // The first event of an alert never changes, so it is fetched once.
      if (opened.current.has(alertId)) return;
      opened.current.add(alertId);
      loadAlertDetails(alertId)
        .then((loaded) => setDetails((current) => ({ ...current, [alertId]: loaded })))
        .catch(() => opened.current.delete(alertId));
    },
    [refreshTimeline],
  );

  const olderCursor = older ? older.next : (snapshot?.olderAlerts ?? null);
  /** Loads the next page of alerts older than the ones shown. Rejects with a message for the user. */
  const loadOlder = useCallback(async () => {
    if (!olderCursor) return 0;
    const page = await loadOlderAlerts(olderCursor);
    setOlder((current) => ({ items: [...(current?.items ?? []), ...page.items], next: page.nextCursor ?? null }));
    return page.items.length;
  }, [olderCursor]);

  /** What was loaded, with the older alerts appended. */
  const loaded = useMemo(() => {
    if (!snapshot || !older?.items.length) return snapshot;
    const shown = new Set(snapshot.alerts.map((alert) => alert.alertId));
    return { ...snapshot, alerts: [...snapshot.alerts, ...older.items.filter((alert) => !shown.has(alert.alertId))] };
  }, [snapshot, older]);

  const orgId = profile?.org?.orgId;
  const userId = profile?.userId;
  useEffect(() => {
    if (!orgId || !userId) return;
    const config = {
      realtimeEndpoint: backend.eventsRealtimeEndpoint,
      httpEndpoint: backend.eventsHttpEndpoint,
      getIdToken: idToken,
    };
    let subscribedBefore = false;
    const feed = subscribe<Api.AlertMessage>(config, `/alerts/${orgId}`, {
      onStatus: (isConnected) => {
        setConnected(isConnected);
        // Messages sent while disconnected are not replayed, so catch up after a reconnect.
        if (isConnected && subscribedBefore) reload().catch(() => undefined);
        if (isConnected) subscribedBefore = true;
      },
      onMessage: ({ type, alert }) => {
        if (type === "alert.deleted") {
          const remaining = (alerts: Api.Alert[]) => alerts.filter((other) => other.alertId !== alert.alertId);
          setSnapshot((current) => current && { ...current, alerts: remaining(current.alerts) });
          setOlder((current) => current && { ...current, items: remaining(current.items) });
          return;
        }
        setSnapshot((current) =>
          current && {
            ...current,
            alerts: [alert, ...current.alerts.filter((other) => other.alertId !== alert.alertId)].sort((a, b) =>
              b.alertId.localeCompare(a.alertId),
            ),
          },
        );
        if (opened.current.has(alert.alertId)) refreshTimeline(alert.alertId).catch(() => undefined);
      },
    });
    const popups = subscribe<Api.NotificationMessage>(config, `/notifications/${orgId}/${userId}`, {
      onMessage: setPopup,
    });
    return () => {
      feed.close();
      popups.close();
    };
  }, [orgId, userId, reload, refreshTimeline]);

  const data = useMemo(
    () => pending ?? (loaded ? toWorkspace(loaded, extras, timelines, details) : EMPTY),
    [pending, loaded, extras, timelines, details],
  );

  /** Saves the difference between the current workspace and `next`. Rejects with a message for the user. */
  const commit = useCallback(
    async (next: Data): Promise<CommitResult> => {
      const snapshot = loaded;
      if (!snapshot) throw new Error("The workspace is not loaded yet.");
      const nextExtras = extrasOf(next, extras);
      const changes = planChanges(data, next);
      setExtras(nextExtras);
      if (!changes.length) return { data: next, changes, created: { rules: {}, books: {}, groups: {} }, notes: [] };
      setPending(next);
      try {
        const { created, notes, alerts } = await applyChanges(changes, next, snapshot);
        if (changes.every((change) => change.kind === "alert.checks")) {
          // Ticking a runbook step changes nothing else, so the alerts the server sent back are enough.
          const sent = new Map(alerts.map((alert) => [alert.alertId, alert]));
          setSnapshot(
            (current) => current && { ...current, alerts: current.alerts.map((alert) => sent.get(alert.alertId) ?? alert) },
          );
          setOlder(
            (current) => current && { ...current, items: current.items.map((alert) => sent.get(alert.alertId) ?? alert) },
          );
          return { data: next, changes, created, notes };
        }
        const deleted = new Set(changes.flatMap((change) => (change.kind === "alert.delete" ? [change.alertId] : [])));
        if (deleted.size) {
          setOlder((current) => current && { ...current, items: current.items.filter((alert) => !deleted.has(alert.alertId)) });
        }
        // What this browser keeps for a new rule was filed under the draft's id; the server gave it its own.
        const kept: Extras = {
          ...nextExtras,
          windows: {
            ...nextExtras.windows,
            ...Object.fromEntries(Object.entries(created.rules).map(([draft, id]) => [id, nextExtras.windows[draft]])),
          },
        };
        setExtras(kept);
        const reloaded = await reload();
        const touched = new Set(
          changes.flatMap((change) => ("alertId" in change && !deleted.has(change.alertId) ? [change.alertId] : [])),
        );
        const fresh: Timelines = { ...timelines };
        for (const alertId of touched) fresh[alertId] = await loadTimeline(alertId);
        setTimelines(fresh);
        return { data: reloaded ? toWorkspace(reloaded, kept, fresh, details) : next, changes, created, notes };
      } finally {
        setPending(null);
      }
    },
    [loaded, data, extras, timelines, details, reload, setExtras],
  );

  const join = useCallback(
    async (inviteCode: string) => {
      await api("POST", "/orgs/join", { inviteCode });
      await reload();
    },
    [reload],
  );
  const create = useCallback(
    async (name: string) => {
      await api("POST", "/orgs", { name });
      await reload();
    },
    [reload],
  );

  return {
    status,
    profile,
    snapshot,
    data,
    connected,
    popup,
    setPopup,
    commit,
    openAlert,
    retry,
    join,
    create,
    hasOlderAlerts: Boolean(olderCursor),
    loadOlder,
  };
}

export type LiveWorkspace = ReturnType<typeof useLiveWorkspace>;
