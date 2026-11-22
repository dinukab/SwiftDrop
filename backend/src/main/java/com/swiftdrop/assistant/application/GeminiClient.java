package com.swiftdrop.assistant.application;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
        this.http = RestClient.builder().baseUrl(BASE_URL).build();
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
            response = http.post()
                    .uri("/v1beta/models/{model}:generateContent?key={key}",
                            properties.model(), properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RuntimeException e) {
            throw new AssistantUnavailableException("Gemini request failed: " + e.getMessage());
        }

        JsonNode content = response != null ? response.path("candidates").path(0).path("content") : null;
        if (content == null || content.isMissingNode()) {
            throw new AssistantUnavailableException("Gemini returned no content");
        }
        return content;
    }

    /** Static failure so the controller can map it without leaking provider details. */
    static final class AssistantUnavailableException extends RuntimeException {
        AssistantUnavailableException(String message) {
            super(message);
        }
    }
}
