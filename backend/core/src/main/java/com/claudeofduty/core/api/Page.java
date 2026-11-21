package com.claudeofduty.core.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** One page of a list. {@code nextCursor} is absent on the last page. */
public record Page<T>(List<T> items, @JsonInclude(JsonInclude.Include.NON_NULL) String nextCursor) {

    public static <T> Page<T> of(List<T> items) {
        return new Page<>(items, null);
    }
}
