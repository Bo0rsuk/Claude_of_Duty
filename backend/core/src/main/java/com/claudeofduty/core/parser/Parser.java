package com.claudeofduty.core.parser;

import java.util.List;
import java.util.Map;

/**
 * Turns one raw payload into events.
 * To support a new payload format, implement this and register it in {@link ParserRegistry}.
 */
public interface Parser {

    /**
     * One event found in a payload.
     *
     * @param fields the event's values as a flat map
     * @param raw    the part of the payload this event came from
     */
    record ParsedEvent(Map<String, Object> fields, String raw) {
    }

    /** @throws UnparsablePayloadException when the payload is not in this parser's format */
    List<ParsedEvent> parse(String payload);
}
