package com.swiftdrop.assistant.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "swiftdrop.assistant.groq")
public record GroqProperties(
        String apiKey,
        String model
) {
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "GroqProperties[model=" + model + ", apiKey=<redacted>]";
    }
}