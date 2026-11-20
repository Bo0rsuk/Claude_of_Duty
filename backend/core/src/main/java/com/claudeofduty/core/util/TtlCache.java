package com.claudeofduty.core.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Remembers loaded values for a fixed time. This is what lets configuration
 * changes made in the UI reach running Lambdas without a restart.
 */
public final class TtlCache<K, V> {

    private record Entry<V>(V value, Instant expiresAt) {
    }

    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    public TtlCache(Duration ttl) {
        this(ttl, Clock.systemUTC());
    }

    public TtlCache(Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    public V get(K key, Function<K, V> loader) {
        Instant now = clock.instant();
        Entry<V> entry = entries.get(key);
        if (entry == null || !now.isBefore(entry.expiresAt())) {
            entry = new Entry<>(loader.apply(key), now.plus(ttl));
            entries.put(key, entry);
        }
        return entry.value();
    }
}
