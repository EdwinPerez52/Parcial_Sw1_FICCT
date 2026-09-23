package com.collabmodeler.api.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AiServiceTest {
    @Test void analyzeImageReturnsValidProposalFromVisionProvider() {
        var properties = new AiProperties(); properties.setApiKey("test-key"); properties.setBaseUrl("https://example.test/v1");
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        String chatResponse = """
            {"choices":[{"message":{"content":"{\\"classes\\":[{\\"name\\":\\"Product\\",\\"attributes\\":[{\\"name\\":\\"id\\",\\"type\\":\\"UUID\\",\\"primaryKey\\":true,\\"required\\":true,\\"unique\\":true}]}],\\"associations\\":[],\\"warnings\\":[],\\"confidence\\":0.85}"}}]}
            """;
        server.expect(requestTo("https://example.test/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-key"))
            .andExpect(content().string(containsString("json_schema")))
            .andExpect(content().string(containsString("\"detail\":\"high\"")))
            .andRespond(withSuccess(chatResponse, MediaType.APPLICATION_JSON));
        byte[] pngBytes = new byte[24];
        byte[] header = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        System.arraycopy(header, 0, pngBytes, 0, header.length);
        System.arraycopy("IHDR".getBytes(), 0, pngBytes, 12, 4);
        pngBytes[19] = 10; pngBytes[23] = 20;
        var result = new AiService(properties, new ObjectMapper(), builder).analyzeImage(pngBytes, "image/png");
        assertEquals("Product", result.path("classes").path(0).path("name").asText());
        server.verify();
    }

    @Test void restClientExceptionBecomesAiUnavailableInsteadOf500() {
        var properties = new AiProperties(); properties.setApiKey("test-key"); properties.setBaseUrl("https://example.test/v1");
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v1/chat/completions"))
            .andRespond(withServerError());
        var service = new AiService(properties, new ObjectMapper(), builder);
        assertThrows(AiUnavailableException.class, () -> service.analyzeImage(new byte[]{1}, "image/png"));
    }

    @Test void blankKeyThrowsAiUnavailable() {
        var properties = new AiProperties(); properties.setApiKey("");
        var service = new AiService(properties, new ObjectMapper(), RestClient.builder());
        assertThrows(AiUnavailableException.class, () -> service.analyzeImage(new byte[]{1}, "image/png"));
    }
}
