package com.claudeofduty.core.service;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** The parts of create, replace and delete that differ between kinds of configuration objects. */
public interface EntityPolicy<T> {

    /** Fills in values the client may leave out. {@code existing} is null on create. */
    default void applyDefaults(ObjectNode body, T existing) {
    }

    /** @throws com.claudeofduty.core.api.ApiException when the object must not be saved */
    void validate(T item);

    default void afterDelete(String orgId, String id) {
    }
}
