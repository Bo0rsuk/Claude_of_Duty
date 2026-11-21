package com.claudeofduty.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.NotificationJob;
import com.claudeofduty.core.notify.NotificationChannel;
import com.claudeofduty.core.notify.NotificationDelivery;
import com.claudeofduty.core.push.Vapid;
import com.claudeofduty.aws.Env;
import com.claudeofduty.aws.SignedHttp;
import com.claudeofduty.aws.AppSyncEventsClient;
import com.claudeofduty.aws.AwsClients;
import com.claudeofduty.aws.DynamoAlertStore;
import com.claudeofduty.aws.DynamoPushSubscriptions;
import com.claudeofduty.aws.SqsNotificationQueue;
import com.claudeofduty.aws.SsmSecret;
import com.claudeofduty.lambda.channels.EmailChannel;
import com.claudeofduty.lambda.channels.SimulatedVoiceChannel;
import com.claudeofduty.lambda.channels.SmsChannel;
import com.claudeofduty.lambda.channels.UiChannel;
import com.claudeofduty.lambda.channels.WebPushChannel;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Consumes notification jobs from the channel queues and their dead-letter
 * queues. Jobs from a channel queue are delivered; jobs from a dead-letter
 * queue are sent back for another round or given up on.
 */
public class NotifierHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final String DEAD_LETTER_SUFFIX = "-dlq";

    private final NotificationDelivery delivery;

    public NotifierHandler() {
        this(defaultDelivery());
    }

    NotifierHandler(NotificationDelivery delivery) {
        this.delivery = delivery;
    }

    /** Reports the failed messages back, so the queue retries only those and not the whole batch. */
    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            if (!handle(message)) {
                failures.add(new SQSBatchResponse.BatchItemFailure(message.getMessageId()));
            }
        }
        return new SQSBatchResponse(failures);
    }

    private boolean handle(SQSEvent.SQSMessage message) {
        NotificationJob job;
        try {
            job = Json.read(message.getBody(), NotificationJob.class);
        } catch (UncheckedIOException e) {
            // Retrying cannot fix a message that is not a job, so it is dropped.
            System.err.println("Dropping a queue message that is not a notification job: " + e.getMessage());
            return true;
        }
        if (message.getEventSourceArn().endsWith(DEAD_LETTER_SUFFIX)) {
            try {
                delivery.retryOrGiveUp(job);
                return true;
            } catch (RuntimeException e) {
                System.err.println("Could not requeue notification " + job.notificationId() + ": " + e);
                return false;
            }
        }
        return delivery.deliver(job, receiveCount(message));
    }

    private static int receiveCount(SQSEvent.SQSMessage message) {
        String count = message.getAttributes() == null ? null : message.getAttributes().get("ApproximateReceiveCount");
        return count == null ? 1 : Integer.parseInt(count);
    }

    private static NotificationDelivery defaultDelivery() {
        Clock clock = Clock.systemUTC();
        SignedHttp signedHttp = AwsClients.signedHttp();
        AppSyncEventsClient events =
                new AppSyncEventsClient(signedHttp, URI.create(Env.required("EVENTS_HTTP_ENDPOINT")));
        // To add a channel: implement NotificationChannel, add it here and in ChannelDirectory, and give it a queue.
        SsmSecret pushPublicKey = new SsmSecret(
                signedHttp, AwsClients.region(), Env.get("VAPID_PUBLIC_KEY_PARAMETER", "/cod/vapid-public-key"));
        SsmSecret pushPrivateKey = new SsmSecret(
                signedHttp, AwsClients.region(), Env.get("VAPID_PRIVATE_KEY_PARAMETER", "/cod/vapid-private-key"));
        Map<String, NotificationChannel> channels = Map.of(
                "sms", new SmsChannel(AwsClients.sns()),
                "email", new EmailChannel(AwsClients.ses(), Env.get("SENDER_EMAIL", "")),
                "ui", new UiChannel(events, clock),
                "push", new WebPushChannel(
                        new DynamoPushSubscriptions(AwsClients.dynamoDb()),
                        () -> new Vapid.Keys(pushPublicKey.get(), pushPrivateKey.get()),
                        Env.get("VAPID_SUBJECT", "mailto:alerts@example.com"),
                        Env.get("APP_URL", ""),
                        clock),
                "voice", new SimulatedVoiceChannel());
        DynamoAlertStore alerts = new DynamoAlertStore(AwsClients.dynamoDb());
        // Reads that find nothing and a message to a channel nobody subscribes to.
        Priming.run(
                () -> alerts.timeline("priming"),
                () -> events.publish("/notifications/priming/priming", Map.of("type", "priming")),
                () -> Json.read(Json.write(Map.of("channel", "priming")), NotificationJob.class));
        return new NotificationDelivery(
                channels,
                new SqsNotificationQueue(AwsClients.sqs(), Env.required("NOTIFICATION_QUEUE_URLS")),
                alerts::appendTimeline,
                clock);
    }
}
