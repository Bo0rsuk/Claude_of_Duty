package com.claudeofduty.aws;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.NotificationJob;
import com.claudeofduty.core.port.NotificationQueue;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.services.sqs.SqsClient;

/** One SQS queue per notification channel. */
public final class SqsNotificationQueue implements NotificationQueue {

    private final SqsClient sqs;
    private final Map<String, String> queueUrlByChannel = new HashMap<>();

    /** @param queueUrlsJson a JSON object mapping channel names to queue URLs, as Terraform provides it */
    public SqsNotificationQueue(SqsClient sqs, String queueUrlsJson) {
        this.sqs = sqs;
        Json.tree(queueUrlsJson).fields()
                .forEachRemaining(entry -> queueUrlByChannel.put(entry.getKey(), entry.getValue().asText()));
    }

    @Override
    public void enqueue(NotificationJob job, int delaySeconds) {
        String queueUrl = queueUrlByChannel.get(job.channel());
        if (queueUrl == null) {
            throw new IllegalArgumentException("No queue for channel " + job.channel());
        }
        sqs.sendMessage(request -> request.queueUrl(queueUrl).messageBody(Json.write(job)).delaySeconds(delaySeconds));
    }
}
