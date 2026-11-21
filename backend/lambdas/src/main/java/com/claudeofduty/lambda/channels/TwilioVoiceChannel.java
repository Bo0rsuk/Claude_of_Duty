package com.claudeofduty.lambda.channels;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.NotificationJob;
import com.claudeofduty.core.notify.NotificationChannel;
import com.claudeofduty.core.notify.PermanentDeliveryException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Phone calls through Twilio: the recipient's phone rings and a voice reads
 * the notification. Twilio asks our API what to do once the call is answered
 * (see {@link #script}); trial accounts accept a call described no other way.
 */
public final class TwilioVoiceChannel implements NotificationChannel {

    /**
     * The account, its secret, the number calls come from and the address of our
     * endpoint that answers with the call script. Read when a call is placed.
     */
    public record Account(String sid, String authToken, String fromNumber, String scriptUrl) {
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final Supplier<Account> account;
    private final HttpClient http;

    public TwilioVoiceChannel(Supplier<Account> account) {
        this.account = account;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public Map<String, Object> send(NotificationJob job) {
        Account from = account.get();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://api.twilio.com/2010-04-01/Accounts/" + from.sid() + "/Calls.json"))
                .timeout(TIMEOUT)
                .header("authorization", "Basic " + Base64.getEncoder().encodeToString(
                        (from.sid() + ":" + from.authToken()).getBytes(StandardCharsets.UTF_8)))
                .header("content-type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        form(job.recipient().address(), from.fromNumber(), scriptUrl(from.scriptUrl(), job.body()))))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach the phone provider: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while placing the call", e);
        }
        JsonNode body = Json.tree(response.body());
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return Map.of("callId", body.path("sid").asText(), "callStatus", body.path("status").asText());
        }
        String reason = "The phone provider refused the call: " + body.path("message").asText("HTTP " + status);
        // Trying again helps when the provider is busy or down, not when it rejects the number or the account.
        if (status == 429 || status >= 500) {
            throw new IllegalStateException(reason);
        }
        throw new PermanentDeliveryException(reason);
    }

    /** The address Twilio fetches when the call is answered. It carries the text to read. */
    static String scriptUrl(String endpoint, String message) {
        return endpoint + "?text=" + URLEncoder.encode(message == null ? "" : message, StandardCharsets.UTF_8);
    }

    /**
     * What the call does once answered, as TwiML: play the recording when there is one, then read
     * the message twice, since the first words are easily missed.
     *
     * @param recordingUrl public address of a recorded message (mp3 or wav), or empty for none
     */
    public static String script(String message, String recordingUrl) {
        String spoken = escape(message == null || message.isBlank() ? "You have a new alert." : message);
        String recording = recordingUrl == null || recordingUrl.isBlank() ? "" : "<Play>" + escape(recordingUrl) + "</Play>";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response>" + recording
                + "<Say language=\"en-US\">" + spoken + "</Say><Pause length=\"1\"/>"
                + "<Say language=\"en-US\">Again: " + spoken + "</Say></Response>";
    }

    static String form(String to, String from, String scriptUrl) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("To", to);
        fields.put("From", from);
        fields.put("Url", scriptUrl);
        return fields.entrySet().stream()
                .map(field -> field.getKey() + "=" + URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
