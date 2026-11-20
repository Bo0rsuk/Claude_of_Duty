package com.claudeofduty.core;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.Source;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/** Gives tests access to the JSON examples in Docs/contracts. */
final class ContractDocs {

    private static final Path CONTRACTS = Path.of("..", "..", "Docs", "contracts");
    private static final Pattern JSON_BLOCK = Pattern.compile("```json\\n(.*?)```", Pattern.DOTALL);

    private ContractDocs() {
    }

    static List<String> examples(String file) {
        try {
            return JSON_BLOCK.matcher(Files.readString(CONTRACTS.resolve(file)))
                    .results()
                    .map(result -> result.group(1))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The first example in the file that contains the marker. */
    static <T> T example(String file, String marker, Class<T> type) {
        String json = examples(file).stream()
                .filter(block -> block.contains(marker))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No JSON example containing " + marker + " in " + file));
        return Json.read(json, type);
    }

    /** The five mock source presets, in document order: JSON API, application log, access log, Kafka, webhook. */
    static List<Source> mockPresets() {
        return examples("sources-and-events.md").stream()
                .filter(json -> json.contains("\"connector\"") && !json.contains("\"sourceId\""))
                .map(json -> Json.read(json, Source.class))
                .toList();
    }
}
