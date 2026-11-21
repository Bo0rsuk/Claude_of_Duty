package com.claudeofduty.worker;

import com.claudeofduty.aws.AwsClients;
import com.claudeofduty.aws.DynamoSourceDirectory;
import com.claudeofduty.aws.Env;
import com.claudeofduty.aws.KinesisRawRecordSink;
import com.claudeofduty.core.port.RawRecordSink;
import com.claudeofduty.core.port.SourceDirectory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The connector worker: a long-running process that pulls from HTTP and Kafka
 * sources and puts what it reads on the same stream the webhook endpoint writes to.
 *
 * Configuration (environment): STREAM_NAME, TABLE_PREFIX, AWS_REGION.
 */
public final class WorkerMain {

    private static final int REFRESH_SECONDS = 15;
    private static final int POLLING_THREADS = 8;

    private WorkerMain() {
    }

    public static void main(String[] args) {
        SourceDirectory sources = new DynamoSourceDirectory(AwsClients.dynamoDb());
        RawRecordSink sink = new KinesisRawRecordSink(AwsClients.kinesis(), Env.required("STREAM_NAME"));
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(POLLING_THREADS);
        SourceSupervisor supervisor = new SourceSupervisor(ConnectorRegistry.withDefaults(scheduler), sink);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            supervisor.stopAll();
            scheduler.shutdownNow();
        }));
        System.out.println("Worker started, reading sources every " + REFRESH_SECONDS + " s");
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                supervisor.reconcile(sources.all());
            } catch (RuntimeException e) {
                System.err.println("Could not refresh the sources: " + e);
            }
        }, 0, REFRESH_SECONDS, TimeUnit.SECONDS);
    }
}
