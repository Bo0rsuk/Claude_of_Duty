package com.claudeofduty.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.NotificationJob;
import com.claudeofduty.core.model.Severity;
import com.claudeofduty.core.model.TimelineEntry;
import com.claudeofduty.core.notify.NotificationDelivery;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotifierHandlerTest {

    private static final String QUEUE = "arn:aws:sqs:eu-central-1:123456789012:cod-notifications-sms";

    private final List<NotificationJob> requeued = new ArrayList<>();
    private final List<TimelineEntry> timeline = new ArrayList<>();
    private final NotifierHandler handler = new NotifierHandler(new NotificationDelivery(
            Map.of("sms", job -> {
                if (job.recipient().address().equals("+48000000000")) {
                    throw new IllegalStateException("Invalid phone number");
                }
                return Map.of("messageId", "sns-1");
            }),
            (job, delay) -> requeued.add(job),
            timeline::add,
            Clock.systemUTC()));

    @Test
    void onlyFailedJobsOfABatchAreReportedForRetry() {
        SQSBatchResponse response = handler.handleRequest(event(
                message("m-1", QUEUE, job("+48600100200", 1), "1"),
                message("m-2", QUEUE, job("+48000000000", 1), "2"),
                message("m-3", QUEUE, "this is not a job", "1")), null);

        assertEquals(List.of("m-2"), response.getBatchItemFailures().stream()
                .map(SQSBatchResponse.BatchItemFailure::getItemIdentifier).toList());
        assertEquals(List.of(TimelineEntry.Type.NOTIFICATION_SENT, TimelineEntry.Type.NOTIFICATION_FAILED),
                timeline.stream().map(TimelineEntry::type).toList());
        assertEquals(2, timeline.get(1).detail().get("attempt"));
    }

    @Test
    void jobsFromTheDeadLetterQueueGoBackForAnotherRound() {
        SQSBatchResponse response = handler.handleRequest(event(
                message("m-1", QUEUE + "-dlq", job("+48000000000", 1), "1"),
                message("m-2", QUEUE + "-dlq", job("+48000000000", 3), "1")), null);

        assertTrue(response.getBatchItemFailures().isEmpty());
        assertEquals(List.of(2), requeued.stream().map(NotificationJob::attempt).toList());
        assertEquals(List.of(TimelineEntry.Type.NOTIFICATION_FAILED), timeline.stream().map(TimelineEntry::type).toList());
        assertEquals(true, timeline.get(0).detail().get("final"));
    }

    private static String job(String phone, int attempt) {
        return Json.write(new NotificationJob("n-1", "org-1", "alert-1", "sms", Severity.CRITICAL,
                new NotificationJob.Recipient(phone, null, null), null, "text", attempt, Instant.now()));
    }

    private static SQSEvent event(SQSEvent.SQSMessage... messages) {
        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(messages));
        return event;
    }

    private static SQSEvent.SQSMessage message(String id, String queueArn, String body, String receiveCount) {
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId(id);
        message.setEventSourceArn(queueArn);
        message.setBody(body);
        message.setAttributes(Map.of("ApproximateReceiveCount", receiveCount));
        return message;
    }
}
