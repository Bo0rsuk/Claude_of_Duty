package com.claudeofduty.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.claudeofduty.core.alerting.EventProcessor;
import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.RawRecord;
import com.claudeofduty.core.model.RecipientGroup;
import com.claudeofduty.core.notify.ChannelDirectory;
import com.claudeofduty.core.notify.NotificationDispatcher;
import com.claudeofduty.core.notify.NotificationPlanner;
import com.claudeofduty.core.parser.EventNormalizer;
import com.claudeofduty.core.parser.ParserRegistry;
import com.claudeofduty.core.rules.Operators;
import com.claudeofduty.core.rules.RuleMatcher;
import com.claudeofduty.aws.Env;
import com.claudeofduty.aws.SignedHttp;
import com.claudeofduty.aws.AppSyncAlertPublisher;
import com.claudeofduty.aws.AppSyncEventsClient;
import com.claudeofduty.aws.AwsClients;
import com.claudeofduty.aws.DynamoAlertStore;
import com.claudeofduty.aws.DynamoMembership;
import com.claudeofduty.aws.DynamoRecentEvents;
import com.claudeofduty.aws.DynamoRepository;
import com.claudeofduty.aws.DynamoRuleLookup;
import com.claudeofduty.aws.DynamoSourceLookup;
import com.claudeofduty.aws.DynamoThrottle;
import com.claudeofduty.aws.SqsNotificationQueue;
import com.claudeofduty.aws.Tables;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Consumes raw records from the stream and runs them through the pipeline in
 * {@link EventProcessor}. This class only decodes the batch and wires the AWS adapters.
 */
public class ProcessorHandler implements RequestHandler<KinesisEvent, Void> {

    /**
     * Storing an alert is a handful of network calls, so a batch with many distinct
     * alerts is dominated by waiting. These threads do that waiting side by side.
     */
    private static final int STORE_THREADS = 16;
    private static final String PRIMING = "priming";

    private final EventProcessor processor;

    public ProcessorHandler() {
        this(defaultProcessor());
        // Runs the whole pipeline once on a record of an unknown source, which is parsed and then skipped.
        Priming.run(() -> processor.process(
                List.of(new RawRecord(PRIMING, PRIMING, PRIMING, Clock.systemUTC().instant(), "{}"))));
    }

    ProcessorHandler(EventProcessor processor) {
        this.processor = processor;
    }

    @Override
    public Void handleRequest(KinesisEvent event, Context context) {
        List<RawRecord> records = new ArrayList<>(event.getRecords().size());
        for (KinesisEvent.KinesisEventRecord record : event.getRecords()) {
            String json = StandardCharsets.UTF_8.decode(record.getKinesis().getData()).toString();
            try {
                records.add(Json.read(json, RawRecord.class));
            } catch (UncheckedIOException e) {
                context.getLogger().log("Skipping a stream record that is not a RawRecord: " + e.getMessage());
            }
        }
        context.getLogger().log("Processed batch: " + processor.process(records));
        return null;
    }

    private static EventProcessor defaultProcessor() {
        DynamoDbClient dynamoDb = AwsClients.dynamoDb();
        Clock clock = Clock.systemUTC();
        SignedHttp signedHttp = AwsClients.signedHttp();
        AppSyncEventsClient events =
                new AppSyncEventsClient(signedHttp, URI.create(Env.required("EVENTS_HTTP_ENDPOINT")));
        DynamoAlertStore alerts = new DynamoAlertStore(dynamoDb);
        String appUrl = Env.get("APP_URL", "");
        NotificationPlanner planner = new NotificationPlanner(
                ChannelDirectory.withDefaults(),
                new DynamoRepository<>(dynamoDb, Tables.GROUPS, "groupId", RecipientGroup.class),
                new DynamoMembership(dynamoDb),
                new DynamoThrottle(dynamoDb),
                appUrl,
                clock);
        NotificationDispatcher notifier = new NotificationDispatcher(
                planner,
                new SqsNotificationQueue(AwsClients.sqs(), Env.required("NOTIFICATION_QUEUE_URLS")),
                alerts::appendTimeline,
                clock);
        DynamoRuleLookup rules = new DynamoRuleLookup(dynamoDb);
        // None of these touch real data: the lookups find nothing and nobody subscribes to the channel.
        Priming.run(
                () -> rules.enabledRulesFor(PRIMING, PRIMING),
                () -> alerts.find(PRIMING, PRIMING),
                () -> alerts.timeline(PRIMING),
                () -> events.publish("/alerts/" + PRIMING, Map.of("type", PRIMING)));
        return new EventProcessor(
                new EventProcessor.Ports(
                        new DynamoSourceLookup(dynamoDb),
                        rules,
                        alerts,
                        new AppSyncAlertPublisher(events, clock),
                        notifier,
                        new DynamoRecentEvents(dynamoDb, clock)),
                new EventNormalizer(ParserRegistry.withDefaults()),
                new RuleMatcher(Operators.withDefaults()),
                appUrl,
                clock,
                Executors.newFixedThreadPool(STORE_THREADS));
    }
}
