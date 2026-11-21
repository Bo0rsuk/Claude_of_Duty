package com.claudeofduty.core.port;

import java.time.Instant;

/** Counters that cap how many notifications go out in a time window. */
public interface Throttle {

    /**
     * Counts one more notification under the key, unless the key already reached {@code max}.
     *
     * @param windowEnd when the counter stops mattering and may be discarded
     * @return false when the limit was already reached
     */
    boolean tryAcquire(String counterKey, int max, Instant windowEnd);
}
