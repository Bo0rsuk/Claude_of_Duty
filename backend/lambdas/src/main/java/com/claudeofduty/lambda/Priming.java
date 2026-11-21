package com.claudeofduty.lambda;

/**
 * Exercises a function's code paths while Lambda initializes it.
 *
 * With SnapStart the initialized state is saved and reused for every cold
 * start. Whatever runs here (class loading, SDK setup, the first request to a
 * service) is therefore paid once per deployment instead of by the first real
 * event after each cold start.
 */
public final class Priming {

    private Priming() {
    }

    /** Runs each action. A failure is logged and ignored: priming must never stop a function from starting. */
    public static void run(Runnable... actions) {
        for (Runnable action : actions) {
            try {
                action.run();
            } catch (RuntimeException e) {
                System.err.println("Priming step failed (ignored): " + e);
            }
        }
    }
}
