package com.claudeofduty.core.port;

import com.claudeofduty.core.model.Source;
import java.util.List;

public interface SourceDirectory {

    /** Every source of every organization. */
    List<Source> all();
}
