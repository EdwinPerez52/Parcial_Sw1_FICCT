package com.collabmodeler.api.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalAiConfigurationTest {
    @TempDir Path directory;

    private StandardEnvironment load(String profile, boolean fileExists, String override) throws Exception {
        Path dotenv = directory.resolve(".env");
        if (fileExists) Files.writeString(dotenv, "AI_API_KEY=fixture-secret\nAI_TEXT_MODEL=fixture-text\nAI_VISION_MODEL=fixture-vision\nAI_AUDIO_MODEL=fixture-audio\n");
        var environment = new StandardEnvironment();
        // Isolate the test from the developer's actual credentials and environment.
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("test-settings", Map.of(
            "spring.config.location", "classpath:/application.yml")));
        if (override != null) environment.getPropertySources().addFirst(new MapPropertySource("deployment", Map.of("AI_API_KEY", override)));
        var loader = new DefaultResourceLoader() {
            @Override public Resource getResource(String location) {
                if (location.startsWith("file:") && location.endsWith("/.env")) return new FileSystemResource(dotenv);
                return super.getResource(location);
            }
        };
        ConfigDataEnvironmentPostProcessor.applyTo(environment, loader, null, profile);
        return environment;
    }

    @Test void localLaunchLoadsCredentialsAndAllThreeModels() throws Exception {
        var environment = load("dev", true, null);
        assertEquals("fixture-secret", environment.getProperty("app.ai.api-key"));
        assertEquals("fixture-text", environment.getProperty("app.ai.text-model"));
        assertEquals("fixture-vision", environment.getProperty("app.ai.vision-model"));
        assertEquals("fixture-audio", environment.getProperty("app.ai.audio-model"));
    }

    @Test void defaultIdeLaunchAlsoLoadsDotenv() throws Exception {
        assertEquals("fixture-secret", load("default", true, null).getProperty("app.ai.api-key"));
    }

    @Test void deploymentCredentialsTakePrecedence() throws Exception {
        assertEquals("deployment-secret", load("dev", true, "deployment-secret").getProperty("app.ai.api-key"));
    }

    @Test void missingDotenvDoesNotPreventStartup() throws Exception {
        assertEquals("", load("dev", false, null).getProperty("app.ai.api-key"));
    }

    @Test void productionDoesNotLoadLocalCredentials() throws Exception {
        assertEquals("", load("prod", true, null).getProperty("app.ai.api-key"));
    }

    @Test void testsDoNotLoadLocalCredentials() throws Exception {
        assertEquals("", load("test", true, null).getProperty("app.ai.api-key"));
    }
}
