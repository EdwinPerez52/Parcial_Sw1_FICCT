package com.collabmodeler.api.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SpeechTranscriptionServiceTest {
    @Test void sendsMultipartAudioAndReadsTranscript() {
        var properties = new AiProperties(); properties.setApiKey("test-key"); properties.setBaseUrl("https://example.test/v1");
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v1/audio/transcriptions"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-key"))
            .andExpect(request -> {
                String body = new String(((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
                assertTrue(body.contains("name=\"model\""));
                assertTrue(body.contains("gpt-4o-mini-transcribe"));
                assertTrue(body.contains("filename=\"voz.webm\""));
            })
            .andRespond(withSuccess("{\"text\":\"crea una clase Producto\"}", MediaType.APPLICATION_JSON));
        String result = new SpeechTranscriptionService(properties, builder).transcribe(new byte[]{1, 2, 3}, "audio/webm");
        assertEquals("crea una clase Producto", result);
        server.verify();
    }

    @Test void missingKeyNeverSendsAudio() {
        var service = new SpeechTranscriptionService(new AiProperties(), RestClient.builder());
        assertThrows(AiUnavailableException.class, () -> service.transcribe(new byte[]{1}, "audio/webm"));
    }

    @Test void geminiUrlIsRejectedBeforeSendingAudio() {
        var properties = new AiProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://generativelanguage.googleapis.com/v1beta/openai");
        var service = new SpeechTranscriptionService(properties, RestClient.builder());
        var exception = assertThrows(AiUnavailableException.class, () -> service.transcribe(new byte[]{1, 2, 3}, "audio/webm"));
        assertTrue(exception.getMessage().contains("Gemini"));
    }
}
