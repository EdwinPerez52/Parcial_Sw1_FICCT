package com.collabmodeler.api.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class SpeechTranscriptionService {
    private final AiProperties properties;
    private final RestClient rest;

    public SpeechTranscriptionService(AiProperties properties, RestClient.Builder builder) {
        this.properties = properties; this.rest = builder.build();
    }

    public String transcribe(byte[] audio, String mime) {
        if (!"openai".equals(properties.getProvider()) || properties.getApiKey() == null || properties.getApiKey().isBlank())
            throw new AiUnavailableException("Configura AI_PROVIDER=openai y AI_API_KEY para transcribir voz");
        if (properties.getBaseUrl() != null && properties.getBaseUrl().contains("generativelanguage.googleapis.com"))
            throw new AiUnavailableException("La transcripción de audio no está soportada con el proveedor Gemini. Usa el reconocimiento de voz del navegador.");
        String extension = "audio/webm".equals(mime) ? "webm" : "audio/ogg".equals(mime) ? "ogg" : "mp4";
        var body = new MultipartBodyBuilder();
        body.part("model", properties.getAudioModel());
        body.part("file", new ByteArrayResource(audio) {
            @Override public String getFilename() { return "voz." + extension; }
        }).contentType(MediaType.parseMediaType(mime));
        JsonNode response;
        try {
            response = rest.post().uri(properties.getBaseUrl() + "/audio/transcriptions")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .contentType(MediaType.MULTIPART_FORM_DATA).body(body.build()).retrieve().body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new AiUnavailableException("El proveedor de transcripción no está disponible. Revisa AI_API_KEY y vuelve a intentarlo");
        }
        String text = response == null ? "" : response.path("text").asText().trim();
        if (text.isEmpty() || text.length() > properties.getMaxInstructionLength())
            throw new IllegalArgumentException("La transcripción está vacía o excede el límite de la instrucción");
        return text;
    }
}
