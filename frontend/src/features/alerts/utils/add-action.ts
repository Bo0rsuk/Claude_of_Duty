import type { Alert } from "#features/workspace";

export function addAction(a: Alert, title: string, body: string): Alert {
  return { ...a, timeline: [...a.timeline, { time: Date.now(), title, body }] };
}
