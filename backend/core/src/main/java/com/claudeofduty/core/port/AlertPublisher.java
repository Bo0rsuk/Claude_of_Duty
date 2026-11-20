package com.claudeofduty.core.port;

import com.claudeofduty.core.model.Alert;

/** Pushes alert changes to the live feed. Best effort: a failure must not fail processing. */
public interface AlertPublisher {

    String CREATED = "alert.created";
    String UPDATED = "alert.updated";

    void publish(String type, Alert alert);
}
