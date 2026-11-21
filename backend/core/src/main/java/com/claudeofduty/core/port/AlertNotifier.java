package com.claudeofduty.core.port;

import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Source;

/** Told about every newly created alert, so that the people in the rule's routing get notified. */
public interface AlertNotifier {

    void alertCreated(Alert alert, Rule rule, Source source);
}
