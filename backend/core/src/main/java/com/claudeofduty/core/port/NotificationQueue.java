package com.claudeofduty.core.port;

import com.claudeofduty.core.model.NotificationJob;

/** Hands notification jobs to the notifier. There is one queue per channel. */
public interface NotificationQueue {

    void enqueue(NotificationJob job, int delaySeconds);
}
