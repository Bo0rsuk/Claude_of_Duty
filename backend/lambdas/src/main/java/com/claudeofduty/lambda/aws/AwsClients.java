package com.claudeofduty.lambda.aws;

import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.kinesis.KinesisClient;

/** SDK clients sharing one HTTP client. Region and credentials come from the environment. */
public final class AwsClients {

    private static final SdkHttpClient HTTP = UrlConnectionHttpClient.create();

    private AwsClients() {
    }

    public static SdkHttpClient http() {
        return HTTP;
    }

    public static DynamoDbClient dynamoDb() {
        return DynamoDbClient.builder().httpClient(HTTP).build();
    }

    public static KinesisClient kinesis() {
        return KinesisClient.builder().httpClient(HTTP).build();
    }
}
