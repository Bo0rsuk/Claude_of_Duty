package com.claudeofduty.lambda.ai;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.port.TextModel;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Google Gemini through its REST API, asked to answer in JSON. */
public final class GeminiTextModel implements TextModel {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String PATH = "/v1beta/models/%s:generateContent";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final URI endpoint;
    private final Supplier<String> apiKey;
    private final Runnable forgetApiKey;

    /**
     * @param apiKey       called when a request is made, so the key can be loaded lazily
     * @param forgetApiKey called when Gemini rejects the key, so that a rotated key is loaded on the retry
     */
    public GeminiTextModel(String model, Supplier<String> apiKey, Runnable forgetApiKey) {
        this(BASE_URL, model, apiKey, forgetApiKey);
    }

    GeminiTextModel(String baseUrl, String model, Supplier<String> apiKey, Runnable forgetApiKey) {
        this.endpoint = URI.create(baseUrl + PATH.formatted(model));
        this.apiKey = apiKey;
        this.forgetApiKey = forgetApiKey;
    }

    @Override
    public String completeJson(String instructions, String input) {
        String body = Json.write(Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instructions))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", input)))),
                // A low temperature keeps the same goal from producing a different rule on every try.
                "generationConfig", Map.of("responseMimeType", "application/json", "temperature", 0.2)));
        try {
            HttpResponse<String> response = send(body);
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                // The remembered key may have been rotated. Load the current one and try once more.
                forgetApiKey.run();
                response = send(body);
            }
            JsonNode answer = Json.tree(response.body());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Gemini answered HTTP " + response.statusCode() + ": "
                        + answer.path("error").path("message").asText(""));
            }
            StringBuilder text = new StringBuilder();
            answer.path("candidates").path(0).path("content").path("parts")
                    .forEach(part -> text.append(part.path("text").asText("")));
            if (text.isEmpty()) {
                throw new IllegalStateException("Gemini returned no text (finish reason: "
                        + answer.path("candidates").path(0).path("finishReason").asText("unknown") + ")");
            }
            return text.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Gemini could not be reached: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Gemini", e);
        }
    }

    private HttpResponse<String> send(String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(TIMEOUT)
                .header("content-type", "application/json")
                .header("x-goog-api-key", apiKey.get())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
