package com.claudeofduty.aws;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/** SDK clients sharing one HTTP client. Region and credentials come from the environment. */
public final class AwsClients {

    private static final SdkHttpClient HTTP = UrlConnectionHttpClient.create();
    private static final int DYNAMODB_ATTEMPTS = 8;

    private AwsClients() {
    }

    public static SdkHttpClient http() {
        return HTTP;
    }

    /**
     * Retries more often than the SDK's default of 3 attempts. An on-demand table
     * throttles briefly while it scales up to a sudden burst of writes, and waiting
     * that out is far cheaper than failing and reprocessing a whole batch.
     */
    public static DynamoDbClient dynamoDb() {
        return DynamoDbClient.builder()
                .httpClient(HTTP)
                .overrideConfiguration(configuration -> configuration.retryStrategy(
                        AwsRetryStrategy.standardRetryStrategy().toBuilder().maxAttempts(DYNAMODB_ATTEMPTS).build()))
                .build();
    }

    public static KinesisClient kinesis() {
        return KinesisClient.builder().httpClient(HTTP).build();
    }

    public static SqsClient sqs() {
        return SqsClient.builder().httpClient(HTTP).build();
    }

    public static SnsClient sns() {
        return SnsClient.builder().httpClient(HTTP).build();
    }

    public static SesV2Client ses() {
        return SesV2Client.builder().httpClient(HTTP).build();
    }

    /** For the AWS endpoints called without an SDK client. Region and credentials come from the environment. */
    public static SignedHttp signedHttp() {
        return new SignedHttp(HTTP, region(), DefaultCredentialsProvider.create());
    }

    public static Region region() {
        return DefaultAwsRegionProviderChain.builder().build().getRegion();
    }
}
