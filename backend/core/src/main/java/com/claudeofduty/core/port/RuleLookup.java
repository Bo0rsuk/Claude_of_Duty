package com.claudeofduty.core.port;

import com.claudeofduty.core.model.Rule;
import java.util.List;

public interface RuleLookup {

    /** The enabled rules attached to a source. */
    List<Rule> enabledRulesFor(String orgId, String sourceId);
}
