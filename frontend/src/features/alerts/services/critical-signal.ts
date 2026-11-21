import type { Alert } from "#features/workspace/types/records.ts";
import { readLocal, writeLocal } from "#services/local-storage.ts";
import { useEffect, useRef } from "react";

const SOUND_KEY = "fans:critical-sound";

export const soundEnabled = () => readLocal(SOUND_KEY, () => false);

/** Turning the sound on plays it once: the person hears what to expect, and the browser allows audio after a click. */
export function setSoundEnabled(on: boolean) {
  writeLocal(SOUND_KEY, on);
  if (on) chirp();
}

let audio: AudioContext | undefined;

/** Two short rising tones. Generated, so there is no file to load. */
function chirp() {
  try {
    const context = (audio ??= new AudioContext());
    void context.resume();
    const start = context.currentTime;
    [660, 880].forEach((frequency, index) => {
      const tone = context.createOscillator(),
        volume = context.createGain(),
        at = start + index * 0.18;
      tone.frequency.value = frequency;
      volume.gain.setValueAtTime(0.0001, at);
      volume.gain.exponentialRampToValueAtTime(0.2, at + 0.02);
      volume.gain.exponentialRampToValueAtTime(0.0001, at + 0.16);
      tone.connect(volume).connect(context.destination);
      tone.start(at);
      tone.stop(at + 0.17);
    });
  } catch {
    /* No audio on this device; the tab title still carries the signal. */
  }
}

export const openCritical = (alerts: Alert[]) =>
  alerts.filter((alert) => alert.severity === "Critical" && alert.status === "Open");

/**
 * Keeps the number of open critical alerts in the tab title, where it is seen
 * from another tab, and plays a sound when a new one arrives if the person asked for it.
 */
export function useCriticalSignal(alerts: Alert[], title: string) {
  const critical = openCritical(alerts);
  const count = critical.length;
  useEffect(() => {
    document.title = (count ? "(" + count + ") " : "") + title;
  }, [count, title]);

  // Alerts present when the app opens are not news; only later arrivals make a sound.
  const known = useRef<Set<string> | null>(null);
  const ids = critical.map((alert) => alert.id).join(" ");
  useEffect(() => {
    const current = new Set(ids ? ids.split(" ") : []);
    const seen = known.current;
    if (seen && [...current].some((id) => !seen.has(id)) && soundEnabled()) chirp();
    // An empty first load is still loading, not a baseline.
    if (seen || current.size) known.current = current;
  }, [ids]);
}
