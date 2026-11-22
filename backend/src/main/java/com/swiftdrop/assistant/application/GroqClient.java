package com.swiftdrop.assistant.application;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Thin client for the Groq chat-completions endpoint (OpenAI-compatible). Sends the message
 * history plus function-tool declarations and returns the assistant message node; the assistant
 * service interprets {@code tool_calls} and loops until Groq answers in text.
 */
@Component
class GroqClient {

    private static final String BASE_URL = "https://api.groq.com/openai/v1";

    private final RestClient http;
    private final JsonNodeFactory nodes = JsonNodeFactory.instance;
    private final GroqProperties properties;

    GroqClient(GroqProperties properties) {
        this.http = RestClient.builder().baseUrl(BASE_URL).build();
        this.properties = properties;
    }

    boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * One chat-completions round-trip.
     *
     * @param messages conversation messages so far (user/assistant/tool turns)
     * @param tools    OpenAI-style function tool declarations
     * @return the {@code choices[0].message} node
     */
    JsonNode chatCompletion(ArrayNode messages, ArrayNode tools) {
        ObjectNode body = nodes.objectNode();
        body.put("model", properties.model());
        body.set("messages", messages);
        body.set("tools", tools);

        JsonNode response;
        try {
            response = http.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RuntimeException e) {
            throw new AssistantUnavailableException("Groq request failed: " + e.getMessage());
        }

        JsonNode message = response != null ? response.path("choices").path(0).path("message") : null;
        if (message == null || message.isMissingNode()) {
            throw new AssistantUnavailableException("Groq returned no message");
        }
        return message;
    }

    /** Static failure so callers can map it without leaking provider details. */
    static final class AssistantUnavailableException extends RuntimeException {
        AssistantUnavailableException(String message) {
            super(message);
        }
    }
}
