package com.swiftdrop.assistant.application;

import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Thin client for the Gemini {@code generateContent} REST endpoint. Sends the conversation
 * contents plus function declarations and returns the raw response tree; the assistant service
 * interprets {@code functionCall} parts and loops until Gemini answers in text.
 */
@Component
class GeminiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";

    private final RestClient http;
    private final JsonNodeFactory nodes = JsonNodeFactory.instance;
    private final GeminiProperties properties;

    GeminiClient(GeminiProperties properties) {
        // Bounded waits: a stuck call must not hold a request thread, and each tool round can
        // call Gemini again.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.http = RestClient.builder().baseUrl(BASE_URL).requestFactory(requestFactory).build();
        this.properties = properties;
    }

    boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * One generateContent round-trip.
     *
     * @param contents conversation contents so far (user/model/functionResponse turns)
     * @param tools    function declarations for parcel tracking and price quotes
     * @return the response {@code candidates[0].content} node
     */
    JsonNode generateContent(ArrayNode contents, ArrayNode tools) {
        ObjectNode body = nodes.objectNode();
        body.set("contents", contents);
        body.set("tools", tools);

        JsonNode response;
        try {
            // The key goes in a header, never the URL: Spring's I/O error messages and access
            // logs include the full request URL.
            response = http.post()
                    .uri("/v1beta/models/{model}:generateContent", properties.model())
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            // Status only: the response body can echo request details.
            throw new AssistantUnavailableException("Gemini returned HTTP " + e.getStatusCode().value());
        } catch (RuntimeException e) {
            // Class name only: the message of a connection error contains the request URL.
            throw new AssistantUnavailableException("Gemini request failed (" + e.getClass().getSimpleName() + ")");
        }

        JsonNode content = response != null ? response.path("candidates").path(0).path("content") : null;
        if (content == null || content.isMissingNode()) {
            throw new AssistantUnavailableException("Gemini returned no content");
        }
        return content;
    }

    /** Gemini could not answer (overloaded, rate limited, unreachable). Its message never carries the key or URL. */
    static final class AssistantUnavailableException extends RuntimeException {
        AssistantUnavailableException(String message) {
            super(message);
        }
    }
}
