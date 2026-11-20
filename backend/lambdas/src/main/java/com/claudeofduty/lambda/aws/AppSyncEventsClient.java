package com.claudeofduty.lambda.aws;

import com.claudeofduty.core.json.Json;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;
import software.amazon.awssdk.regions.Region;

/**
 * Publishes to an AppSync Events channel over its HTTP endpoint. The SDK has
 * no client for this endpoint, so the request is signed with SigV4 here.
 */
public final class AppSyncEventsClient {

    private static final String SIGNING_NAME = "appsync";

    private final SdkHttpClient http;
    private final URI endpoint;
    private final Region region;
    private final AwsCredentialsProvider credentials;
    private final AwsV4HttpSigner signer = AwsV4HttpSigner.create();

    public AppSyncEventsClient(SdkHttpClient http, URI endpoint, Region region, AwsCredentialsProvider credentials) {
        this.http = http;
        this.endpoint = endpoint;
        this.region = region;
        this.credentials = credentials;
    }

    /** @throws IllegalStateException when AppSync rejects the event */
    public void publish(String channel, Object event) {
        // The API takes each event as a JSON document serialized into a string.
        String body = Json.write(Map.of("channel", channel, "events", List.of(Json.write(event))));

        SdkHttpRequest request = SdkHttpRequest.builder()
                .method(SdkHttpMethod.POST)
                .uri(endpoint)
                .putHeader("content-type", "application/json")
                .build();
        SignedRequest signed = signer.sign(signing -> signing
                .identity(credentials.resolveCredentials())
                .request(request)
                .payload(ContentStreamProvider.fromUtf8String(body))
                .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, SIGNING_NAME)
                .putProperty(AwsV4HttpSigner.REGION_NAME, region.id()));

        try {
            HttpExecuteResponse response = http.prepareRequest(HttpExecuteRequest.builder()
                            .request(signed.request())
                            .contentStreamProvider(signed.payload().orElse(null))
                            .build())
                    .call();
            String responseBody = read(response);
            if (!response.httpResponse().isSuccessful()) {
                throw new IllegalStateException("AppSync Events answered "
                        + response.httpResponse().statusCode() + ": " + responseBody);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(HttpExecuteResponse response) throws IOException {
        if (response.responseBody().isEmpty()) {
            return "";
        }
        try (InputStream stream = response.responseBody().get()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
