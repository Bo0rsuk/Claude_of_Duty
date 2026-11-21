package com.claudeofduty.aws;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
 * Sends a SigV4-signed JSON POST to an AWS endpoint. Used for the two calls
 * where a whole SDK module would be out of proportion: publishing to AppSync
 * Events, which has no SDK client, and reading one parameter from SSM.
 */
public final class SignedHttp {

    public record Response(int status, String body) {

        public boolean successful() {
            return status >= 200 && status < 300;
        }
    }

    private final SdkHttpClient http;
    private final Region region;
    private final AwsCredentialsProvider credentials;
    private final AwsV4HttpSigner signer = AwsV4HttpSigner.create();

    public SignedHttp(SdkHttpClient http, Region region, AwsCredentialsProvider credentials) {
        this.http = http;
        this.region = region;
        this.credentials = credentials;
    }

    public Response post(URI endpoint, String signingName, Map<String, String> headers, String body) {
        SdkHttpRequest.Builder request = SdkHttpRequest.builder().method(SdkHttpMethod.POST).uri(endpoint);
        headers.forEach(request::putHeader);
        SignedRequest signed = signer.sign(signing -> signing
                .identity(credentials.resolveCredentials())
                .request(request.build())
                .payload(ContentStreamProvider.fromUtf8String(body))
                .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, signingName)
                .putProperty(AwsV4HttpSigner.REGION_NAME, region.id()));
        try {
            HttpExecuteResponse response = http.prepareRequest(HttpExecuteRequest.builder()
                            .request(signed.request())
                            .contentStreamProvider(signed.payload().orElse(null))
                            .build())
                    .call();
            return new Response(response.httpResponse().statusCode(), read(response));
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
