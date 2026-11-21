import type { Data } from "#features/workspace/types/records.ts";

export function markAlertViewed(data: Data, id: string, time: number): Data {
  const alert = data.alerts.find((item) => item.id === id);
  if (!alert || alert.timeline.some((entry) => entry.title === "Viewed")) return data;

  const entry = { time, title: "Viewed", body: "Opened in the demo workspace" };
  return {
    ...data,
    alerts: data.alerts.map((item) =>
      item === alert ? { ...alert, timeline: [...alert.timeline, entry] } : item,
    ),
  };
}
