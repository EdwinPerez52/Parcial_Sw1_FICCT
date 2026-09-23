package com.collabmodeler.api.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiPropertiesTest {
    @Test void autoPrefersOpenAiAndSupportsTheLegacyKey() {
        var properties = new AiProperties();
        properties.setApiKey("legacy-openai-key");
        properties.setGeminiApiKey("gemini-key");

        assertEquals("openai", properties.activeProvider());
        assertEquals("legacy-openai-key", properties.activeApiKey());
        assertEquals("gpt-4.1-mini", properties.activeTextModel());
    }

    @Test void autoUsesGeminiWhenItIsTheOnlyConfiguredProvider() {
        var properties = new AiProperties();
        properties.setGeminiApiKey("gemini-key");

        assertEquals("gemini", properties.activeProvider());
        assertEquals("gemini-key", properties.activeApiKey());
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai", properties.activeBaseUrl());
        assertEquals("gemini-3.8-flash", properties.activeTextModel());
        assertEquals(java.util.List.of("gemini-3.8-flash", "gemini-3.5-flash-lite"), properties.activeTextModels());
    }

    @Test void explicitProviderOverridesAutomaticSelection() {
        var properties = new AiProperties();
        properties.setProvider("gemini");
        properties.setOpenaiApiKey("openai-key");
        properties.setGeminiApiKey("gemini-key");

        assertEquals("gemini", properties.activeProvider());
        assertEquals("gemini-key", properties.activeApiKey());
    }

    @Test void rejectsUnknownProvider() {
        var properties = new AiProperties();
        properties.setProvider("unknown");
        assertThrows(AiUnavailableException.class, properties::activeProvider);
    }
}
