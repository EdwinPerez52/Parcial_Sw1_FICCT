package com.collabmodeler.api.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class AiService {
    private final AiProperties properties; private final ObjectMapper mapper; private final RestClient rest;
    public AiService(AiProperties properties, ObjectMapper mapper, RestClient.Builder builder) {
        this.properties = properties; this.mapper = mapper; this.rest = builder.build();
    }
    public JsonNode analyzeImage(byte[] image, String contentType) {
        String prompt = "Extrae solo el diagrama visible, sin inventar. Responde JSON {classes:[{name,attributes:[{name,type,primaryKey,required,unique}]}],associations:[{source,target,sourceCardinality,targetCardinality,name}],warnings:[]}. Tipos válidos: String,Text,Integer,Long,Decimal,Boolean,Date,DateTime,UUID,Binary.";
        String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(image);
        return chat(properties.getVisionModel(), List.of(Map.of("role", "user", "content", List.of(Map.of("type", "text", "text", prompt), Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))))));
    }
    private JsonNode chat(String model, List<Map<String, Object>> messages) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) throw new AiUnavailableException("Configura AI_API_KEY para usar el proveedor de IA");
        JsonNode response = rest.post().uri(properties.getBaseUrl() + "/chat/completions").contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + properties.getApiKey())
            .body(Map.of("model", model, "messages", messages, "temperature", 0, "response_format", Map.of("type", "json_object")))
            .retrieve().body(JsonNode.class);
        String content = response.path("choices").path(0).path("message").path("content").asText();
        try { return mapper.readTree(content); } catch (Exception exception) { throw new IllegalArgumentException("El proveedor de IA devolvió JSON inválido", exception); }
    }
}
