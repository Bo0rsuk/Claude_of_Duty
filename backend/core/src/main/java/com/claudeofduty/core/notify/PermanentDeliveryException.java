package com.claudeofduty.core.notify;

/** A delivery failure that trying again cannot fix, e.g. the user has no device registered for push. */
public class PermanentDeliveryException extends RuntimeException {

    public PermanentDeliveryException(String message) {
        super(message);
    }
}
