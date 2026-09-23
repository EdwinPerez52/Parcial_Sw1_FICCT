package com.collabmodeler.api.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {
    private String provider = "auto";
    /** Legacy OpenAI-compatible settings kept for backwards compatibility. */
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey = "";
    private String textModel = "gpt-4.1-mini";
    private String visionModel = "gpt-4.1-mini";
    private String audioModel = "gpt-4o-mini-transcribe";
    private String openaiApiKey = "";
    private String geminiApiKey = "";
    private String geminiBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai";
    private String geminiTextModel = "gemini-3.8-flash";
    private String geminiVisionModel = "gemini-3.8-flash";
    private String geminiFallbackModel = "gemini-3.5-flash-lite";
    private int proposalMinutes = 15;
    private int maxInstructionLength = 2000;

    public String getProvider() { return provider; } public void setProvider(String value) { provider = value; }
    public String getBaseUrl() { return baseUrl; } public void setBaseUrl(String value) { baseUrl = value; }
    public String getApiKey() { return apiKey; } public void setApiKey(String value) { apiKey = value; }
    public String getTextModel() { return textModel; } public void setTextModel(String value) { textModel = value; }
    public String getVisionModel() { return visionModel; } public void setVisionModel(String value) { visionModel = value; }
    public String getAudioModel() { return audioModel; } public void setAudioModel(String value) { audioModel = value; }
    public String getOpenaiApiKey() { return openaiApiKey; } public void setOpenaiApiKey(String value) { openaiApiKey = value; }
    public String getGeminiApiKey() { return geminiApiKey; } public void setGeminiApiKey(String value) { geminiApiKey = value; }
    public String getGeminiBaseUrl() { return geminiBaseUrl; } public void setGeminiBaseUrl(String value) { geminiBaseUrl = value; }
    public String getGeminiTextModel() { return geminiTextModel; } public void setGeminiTextModel(String value) { geminiTextModel = value; }
    public String getGeminiVisionModel() { return geminiVisionModel; } public void setGeminiVisionModel(String value) { geminiVisionModel = value; }
    public String getGeminiFallbackModel() { return geminiFallbackModel; } public void setGeminiFallbackModel(String value) { geminiFallbackModel = value; }
    public int getProposalMinutes() { return proposalMinutes; } public void setProposalMinutes(int value) { proposalMinutes = value; }
    public int getMaxInstructionLength() { return maxInstructionLength; } public void setMaxInstructionLength(int value) { maxInstructionLength = value; }

    public String activeProvider() {
        String configured = provider == null ? "auto" : provider.trim().toLowerCase(Locale.ROOT);
        if (configured.isBlank() || "auto".equals(configured)) {
            if (hasText(openaiApiKey) || hasText(apiKey)) return "openai";
            if (hasText(geminiApiKey)) return "gemini";
            return "openai";
        }
        if (!"openai".equals(configured) && !"gemini".equals(configured)) {
            throw new AiUnavailableException("AI_PROVIDER debe ser auto, openai o gemini");
        }
        return configured;
    }

    public String activeApiKey() {
        if ("gemini".equals(activeProvider())) return blankToEmpty(geminiApiKey);
        return hasText(openaiApiKey) ? openaiApiKey : blankToEmpty(apiKey);
    }

    public String activeBaseUrl() {
        return stripTrailingSlash("gemini".equals(activeProvider()) ? geminiBaseUrl : baseUrl);
    }

    public String activeTextModel() {
        return "gemini".equals(activeProvider()) ? geminiTextModel : textModel;
    }

    public String activeVisionModel() {
        return "gemini".equals(activeProvider()) ? geminiVisionModel : visionModel;
    }

    public List<String> activeTextModels() { return modelsWithGeminiFallback(activeTextModel()); }
    public List<String> activeVisionModels() { return modelsWithGeminiFallback(activeVisionModel()); }

    public String requiredKeyName() {
        return "gemini".equals(activeProvider()) ? "GEMINI_API_KEY" : "OPENAI_API_KEY (o AI_API_KEY)";
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String blankToEmpty(String value) { return value == null ? "" : value; }
    private List<String> modelsWithGeminiFallback(String primary) {
        if (!"gemini".equals(activeProvider()) || !hasText(geminiFallbackModel) || geminiFallbackModel.equals(primary)) {
            return List.of(primary);
        }
        return List.of(primary, geminiFallbackModel);
    }
    private String stripTrailingSlash(String value) {
        if (value == null) return "";
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
