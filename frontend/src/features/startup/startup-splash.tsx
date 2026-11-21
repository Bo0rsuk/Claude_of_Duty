import { useEffect, useRef } from "react";
import styles from "./startup-splash.module.css";

/** Keep in sync with `--intro-duration` in the CSS module. */
const INTRO_MS = 4800;

export function StartupSplash({ onComplete }: { onComplete?: () => void }) {
  const onCompleteRef = useRef(onComplete);
  onCompleteRef.current = onComplete;

  useEffect(() => {
    // ?intro=1 keeps the splash mounted so the sequence can be reviewed.
    if (new URLSearchParams(window.location.search).has("intro")) return;
    const timer = window.setTimeout(() => onCompleteRef.current?.(), INTRO_MS);
    return () => window.clearTimeout(timer);
  }, []);

  return (
    <div className={styles.splash} role="status" aria-label="Squawk is opening">
      <div className={styles.scene} aria-hidden="true">
        <div className={styles.logoLockup}>
          <img
            className={styles.duck}
            src="/squawk-intro.svg"
            alt=""
            width={226}
            height={169}
          />
        </div>
        <div className={styles.taglineLockup}>
          <span className={styles.tagline}>Grouped alerts. Clear response.</span>
        </div>
      </div>
    </div>
  );
}
