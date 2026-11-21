package com.claudeofduty.core.service;

import com.claudeofduty.core.api.ApiException;
import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.RawRecord;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.model.Source.ParserConfig;
import com.claudeofduty.core.parser.EventNormalizer;
import com.claudeofduty.core.parser.ParserRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** Shows what a parser configuration makes of a pasted sample, before a source is saved. */
public final class PreviewService {

    private static final int MAX_LINES = 50;

    private final ParserRegistry parsers;
    private final Clock clock;

    public PreviewService(ParserRegistry parsers, Clock clock) {
        this.parsers = parsers;
        this.clock = clock;
    }

    /** A text sample is parsed line by line, anything else as one payload. */
    public List<NormalizedEvent> parse(ParserConfig parser, String sample) {
        if (parser == null || sample == null || sample.isBlank()) {
            throw ApiException.invalid("parser and sample are required");
        }
        try {
            parsers.create(parser);
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid("parser: " + e.getMessage());
        }
        List<String> payloads = "regex".equals(parser.format())
                ? sample.lines().filter(line -> !line.isBlank()).limit(MAX_LINES).toList()
                : List.of(sample);

        // A fresh normalizer, so previews of half-finished configurations are not cached.
        EventNormalizer normalizer = new EventNormalizer(parsers);
        List<NormalizedEvent> events = new ArrayList<>();
        for (int i = 0; i < payloads.size(); i++) {
            RawRecord record = new RawRecord("preview-" + i, null, null, clock.instant(), payloads.get(i));
            events.addAll(normalizer.normalize(record, parser));
        }
        return events;
    }

    /** The known source types with ready parser configurations. */
    public static List<Source> presets() {
        return SourcePresets.all();
    }
}
