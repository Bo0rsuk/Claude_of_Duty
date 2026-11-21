package com.claudeofduty.core.alerting;

import com.claudeofduty.core.model.NormalizedEvent;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Picks which events go into the recent-events sample: at most a few per
 * second for each source. The sample only has to be representative, so a busy
 * source does not turn every event into an extra database write.
 */
final class RecentEventSampler {

    static final int PER_SECOND = 5;

    private record Window(long second, int taken) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    RecentEventSampler(Clock clock) {
        this.clock = clock;
    }

    boolean take(NormalizedEvent event) {
        long second = clock.instant().getEpochSecond();
        Window updated = windows.merge(event.orgId() + "/" + event.sourceId(), new Window(second, 1),
                (current, first) -> current.second() == second
                        ? new Window(second, current.taken() + 1)
                        : first);
        return updated.taken() <= PER_SECOND;
    }
}
