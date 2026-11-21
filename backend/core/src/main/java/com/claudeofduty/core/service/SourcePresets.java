package com.claudeofduty.core.service;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.Source;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Source types the application knows out of the box, loaded from source-presets.json. */
final class SourcePresets {

    private static final List<Source> ALL = load();

    private SourcePresets() {
    }

    static List<Source> all() {
        return ALL;
    }

    private static List<Source> load() {
        try (InputStream stream = SourcePresets.class.getResourceAsStream("/source-presets.json")) {
            List<Source> presets = new ArrayList<>();
            for (JsonNode preset : Json.tree(new String(stream.readAllBytes(), StandardCharsets.UTF_8))) {
                presets.add(Json.convert(preset, Source.class));
            }
            return List.copyOf(presets);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
