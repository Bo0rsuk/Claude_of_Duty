package com.claudeofduty.core.parser;

import java.util.List;
import java.util.Map;

/**
 * Turns one raw payload into events, each as a flat map of fields.
 * To support a new payload format, implement this and register it in {@link ParserRegistry}.
 */
public interface Parser {

    /** @throws UnparsablePayloadException when the payload is not in this parser's format */
    List<Map<String, Object>> parse(String payload);
}
