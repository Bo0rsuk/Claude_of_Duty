package com.claudeofduty.core.port;

import com.claudeofduty.core.model.Source;
import java.util.Optional;

public interface SourceLookup {

    Optional<Source> find(String orgId, String sourceId);
}
