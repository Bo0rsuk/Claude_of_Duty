package com.claudeofduty.lambda.channels;

import com.claudeofduty.core.model.NotificationJob;
import com.claudeofduty.core.notify.NotificationChannel;
import java.util.Map;

/**
 * Stands in for phone calls until a number with voice support is available.
 * It places no call: it logs what would be said and marks the timeline entry as simulated.
 */
public final class SimulatedVoiceChannel implements NotificationChannel {

    @Override
    public Map<String, Object> send(NotificationJob job) {
        System.out.println("Simulated call to " + job.recipient().address() + ": " + job.body());
        return Map.of("simulated", true);
    }
}
