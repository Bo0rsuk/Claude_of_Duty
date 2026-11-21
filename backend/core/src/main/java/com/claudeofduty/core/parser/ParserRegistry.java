package com.claudeofduty.core.parser;

import com.claudeofduty.core.model.Source.ParserConfig;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Maps the "format" of a parser configuration to the parser that handles it. */
public final class ParserRegistry {

    private final Map<String, Function<ParserConfig, Parser>> factories = new ConcurrentHashMap<>();

    public static ParserRegistry withDefaults() {
        ParserRegistry registry = new ParserRegistry();
        registry.register("json", config -> new JsonParser(config.split()));
        registry.register("regex", config -> new RegexParser(config.pattern()));
        registry.register("kv", config -> new KeyValueParser());
        return registry;
    }

    public void register(String format, Function<ParserConfig, Parser> factory) {
        factories.put(format, factory);
    }

    /** @throws IllegalArgumentException for an unknown format or an invalid configuration */
    public Parser create(ParserConfig config) {
        Function<ParserConfig, Parser> factory = factories.get(config.format());
        if (factory == null) {
            throw new IllegalArgumentException("Unknown parser format: " + config.format());
        }
        return factory.apply(config);
    }
}
