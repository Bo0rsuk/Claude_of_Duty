package com.claudeofduty.worker;

/**
 * Brings payloads of one source into the system, running until it is closed.
 * To support a new connector type, implement this and register it in {@link ConnectorRegistry}.
 */
public interface Connector extends AutoCloseable {

    void start();

    @Override
    void close();
}
