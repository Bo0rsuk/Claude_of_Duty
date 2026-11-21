package com.claudeofduty.core.port;

import java.util.List;
import java.util.Optional;

/** Storage for one kind of object that belongs to an organization. */
public interface Repository<T> {

    List<T> list(String orgId);

    Optional<T> find(String orgId, String id);

    void save(T item);

    void delete(String orgId, String id);
}
