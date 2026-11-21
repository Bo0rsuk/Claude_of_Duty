package com.claudeofduty.core.port;

import com.claudeofduty.core.model.NormalizedEvent;
import java.util.List;

/**
 * A short-lived sample of the events each source produced lately. It backs the
 * rule preview ("this rule would have caught 7 of the last 100 events") and
 * gives the rule assistant real field names and values to work with.
 */
public interface RecentEvents {

    /** Used where no sample is kept, e.g. in tests. */
    RecentEvents NONE = new RecentEvents() {
        @Override
        public void saveAll(List<NormalizedEvent> events) {
        }

        @Override
        public List<NormalizedEvent> latest(String orgId, String sourceId, int limit) {
            return List.of();
        }
    };

    void saveAll(List<NormalizedEvent> events);

    /** Newest first. */
    List<NormalizedEvent> latest(String orgId, String sourceId, int limit);
}
