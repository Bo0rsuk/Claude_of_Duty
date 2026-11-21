import { setSoundEnabled, soundEnabled } from "#features/alerts/services/critical-signal.ts";
import { useState } from "react";

/** Lets the person hear a new critical alert while the app is open in a background tab. */
export function CriticalSound() {
  const [on, setOn] = useState(soundEnabled);
  return (
    <label className="check-line">
      <input
        type="checkbox"
        checked={on}
        onChange={(e) => {
          setOn(e.target.checked);
          setSoundEnabled(e.target.checked);
        }}
      />
      Play a sound when a new critical alert arrives
    </label>
  );
}
