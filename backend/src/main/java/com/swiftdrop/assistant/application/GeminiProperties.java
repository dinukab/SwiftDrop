package com.swiftdrop.assistant.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Gemini API settings. Bound from {@code swiftdrop.assistant.gemini.*}; the API key comes from
 * {@code GEMINI_API_KEY} and may be blank, in which case the assistant replies that it is not
 * configured instead of failing.
 *
 * @param apiKey Gemini API key (never logged)
 * @param model  Gemini model name, e.g. {@code gemini-2.0-flash}
 */
@ConfigurationProperties("swiftdrop.assistant.gemini")
public record GeminiProperties(String apiKey, String model) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Never includes the key. */
    @Override
    public String toString() {
        return "GeminiProperties[model=" + model + ", apiKey=<redacted>]";
    }
}
