package com.swiftdrop.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

class AssistantServiceTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private GeminiClient gemini;
    private AssistantTools tools;
    private AssistantService service;

    @BeforeEach
    void setUp() {
        gemini = mock(GeminiClient.class);
        tools = mock(AssistantTools.class);
        service = new AssistantService(gemini, tools);
    }

    @Test
    void repliesThatItIsNotConfiguredWithoutCallingGemini() {
        when(gemini.isConfigured()).thenReturn(false);

        assertThat(service.chat("Where is my parcel?")).isEqualTo(AssistantService.NOT_CONFIGURED_REPLY);

        verify(gemini, never()).generateContent(any(), any());
    }

    @Test
    void answersFriendlyWhenGeminiIsOverloadedInsteadOfFailing() {
        when(gemini.isConfigured()).thenReturn(true);
        when(gemini.generateContent(any(), any()))
                .thenThrow(new GeminiClient.AssistantUnavailableException("Gemini returned HTTP 503"));

        assertThat(service.chat("Where is my parcel?")).isEqualTo(AssistantService.UNAVAILABLE_REPLY);
    }

    @Test
    void runsTheRequestedToolAndReturnsGeminisFinalAnswer() {
        when(gemini.isConfigured()).thenReturn(true);
        JsonNode askForParcel = MAPPER.readTree("""
                {"role":"model","parts":[{"functionCall":{"name":"get_parcel_status","args":{"trackingNumber":"SD7K3M9Q2XWB"}}}]}
                """);
        JsonNode finalAnswer = MAPPER.readTree("""
                {"role":"model","parts":[{"text":"Your parcel is out for delivery."}]}
                """);
        when(gemini.generateContent(any(ArrayNode.class), any(ArrayNode.class)))
                .thenReturn(askForParcel, finalAnswer);
        when(tools.execute(any(), any())).thenReturn(Map.of("status", "OUT_FOR_DELIVERY"));

        String reply = service.chat("Where is SD7K3M9Q2XWB?");

        assertThat(reply).isEqualTo("Your parcel is out for delivery.");
        verify(tools).execute(
                eq("get_parcel_status"),
                argThat(args -> "SD7K3M9Q2XWB".equals(args.path("trackingNumber").asText())));
    }

    @Test
    void tellsGeminiTheToolFailedInsteadOfCrashing() {
        when(gemini.isConfigured()).thenReturn(true);
        JsonNode askForParcel = MAPPER.readTree("""
                {"role":"model","parts":[{"functionCall":{"name":"get_parcel_status","args":{"trackingNumber":"SD7K3M9Q2XWB"}}}]}
                """);
        JsonNode finalAnswer = MAPPER.readTree("""
                {"role":"model","parts":[{"text":"I could not look that up."}]}
                """);
        when(gemini.generateContent(any(ArrayNode.class), any(ArrayNode.class)))
                .thenReturn(askForParcel, finalAnswer);
        when(tools.execute(any(), any())).thenThrow(new IllegalStateException("database down"));

        assertThat(service.chat("Where is SD7K3M9Q2XWB?")).isEqualTo("I could not look that up.");
    }
}
