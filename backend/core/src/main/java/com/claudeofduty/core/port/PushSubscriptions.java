package com.claudeofduty.core.port;

import com.claudeofduty.core.model.PushSubscription;
import java.util.List;

public interface PushSubscriptions {

    List<PushSubscription> of(String userId);

    void save(PushSubscription subscription);

    void delete(String userId, String subscriptionId);
}
