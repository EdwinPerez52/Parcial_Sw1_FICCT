package com.collabmodeler.api.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
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
        String prompt = "Extrae solo el diagrama visible, sin inventar. Responde JSON {classes:[{name,attributes:[{name,type,primaryKey,required,unique}]}],associations:[{source,target,sourceCardinality,targetCardinality,name}],warnings:[],confidence:0.0}. confidence es un número entre 0 y 1 para la certeza global. Advierte toda ambigüedad. Tipos válidos: String,Text,Integer,Long,Decimal,Boolean,Date,DateTime,UUID,Binary. Cardinalidades válidas: 0..1,1,0..*,1..*.";
        String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(image);
        return ImageProposalValidator.validate(chat(properties.getVisionModel(), List.of(Map.of("role", "user", "content", List.of(Map.of("type", "text", "text", prompt), Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)))))));
    }
    private JsonNode chat(String model, List<Map<String, Object>> messages) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) throw new AiUnavailableException("Configura AI_API_KEY para usar el proveedor de IA");

        List<String> modelsToTry = new java.util.ArrayList<>();
        modelsToTry.add(model);
        if (properties.getBaseUrl() != null && properties.getBaseUrl().contains("generativelanguage.googleapis.com")) {
            if (!"gemini-flash-latest".equals(model)) modelsToTry.add("gemini-flash-latest");
            if (!"gemini-flash-lite-latest".equals(model)) modelsToTry.add("gemini-flash-lite-latest");
        }

        RestClientException lastException = null;
        for (String candidateModel : modelsToTry) {
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    JsonNode response = rest.post().uri(properties.getBaseUrl() + "/chat/completions").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + properties.getApiKey())
                        .body(Map.of("model", candidateModel, "messages", messages, "temperature", 0, "response_format", Map.of("type", "json_object")))
                        .retrieve().body(JsonNode.class);
                    String content = response == null ? "" : response.path("choices").path(0).path("message").path("content").asText();
                    return mapper.readTree(content);
                } catch (RestClientException exception) {
                    lastException = exception;
                    String msg = exception.getMessage() == null ? "" : exception.getMessage();
                    if (msg.contains("503") || msg.contains("high demand") || msg.contains("429")) {
                        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                        continue;
                    }
                    break;
                } catch (Exception exception) {
                    throw new IllegalArgumentException("El proveedor de IA devolvió JSON inválido", exception);
                }
            }
        }
        throw new AiUnavailableException("El proveedor de IA no respondió correctamente. Revisa AI_API_KEY y AI_VISION_MODEL. Detalle: " + (lastException != null ? lastException.getMessage() : "Desconocido"));
    }
}
