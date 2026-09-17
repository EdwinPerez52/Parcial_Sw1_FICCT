package com.collabmodeler.api.config;

import com.collabmodeler.api.collaboration.DiagramChannelInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class WebSocketConfigTest {
    @Test
    void rejectsWildcardOriginInProduction() {
        var environment = new MockEnvironment(); environment.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class,
            () -> new WebSocketConfig(mock(DiagramChannelInterceptor.class), "*", environment));
    }

    @Test
    void permitsExplicitProductionOrigin() {
        var environment = new MockEnvironment(); environment.setActiveProfiles("prod");
        assertDoesNotThrow(() -> new WebSocketConfig(mock(DiagramChannelInterceptor.class),
            "https://modeler.example", environment));
    }
}
