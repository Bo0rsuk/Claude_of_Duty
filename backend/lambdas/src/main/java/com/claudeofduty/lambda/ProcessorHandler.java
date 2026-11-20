package com.claudeofduty.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.claudeofduty.core.alerting.EventProcessor;
import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.RawRecord;
import com.claudeofduty.core.parser.EventNormalizer;
import com.claudeofduty.core.parser.ParserRegistry;
import com.claudeofduty.core.rules.Operators;
import com.claudeofduty.core.rules.RuleMatcher;
import com.claudeofduty.lambda.aws.AppSyncAlertPublisher;
import com.claudeofduty.lambda.aws.AppSyncEventsClient;
import com.claudeofduty.lambda.aws.AwsClients;
import com.claudeofduty.lambda.aws.DynamoAlertStore;
import com.claudeofduty.lambda.aws.DynamoRuleLookup;
import com.claudeofduty.lambda.aws.DynamoSourceLookup;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Consumes raw records from the stream and runs them through the pipeline in
 * {@link EventProcessor}. This class only decodes the batch and wires the AWS adapters.
 */
public class ProcessorHandler implements RequestHandler<KinesisEvent, Void> {

    private final EventProcessor processor;

    public ProcessorHandler() {
        this(defaultProcessor());
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
        AppSyncEventsClient events = new AppSyncEventsClient(
                AwsClients.http(),
                URI.create(Env.required("EVENTS_HTTP_ENDPOINT")),
                Region.of(Env.required("AWS_REGION")),
                DefaultCredentialsProvider.create());
        return new EventProcessor(
                new DynamoSourceLookup(dynamoDb),
                new DynamoRuleLookup(dynamoDb),
                new DynamoAlertStore(dynamoDb),
                new AppSyncAlertPublisher(events, clock),
                new EventNormalizer(ParserRegistry.withDefaults()),
                new RuleMatcher(Operators.withDefaults()),
                Env.get("APP_URL", ""),
                clock);
    }
}
