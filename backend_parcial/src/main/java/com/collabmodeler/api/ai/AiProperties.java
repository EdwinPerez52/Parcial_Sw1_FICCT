package com.collabmodeler.api.ai;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {
    private String provider = "openai";
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey = "";
    private String textModel = "gpt-4.1-mini";
    private String visionModel = "gpt-4.1-mini";
    private int proposalMinutes = 15;
    private int maxInstructionLength = 2000;
    public String getProvider() { return provider; } public void setProvider(String value) { provider = value; }
    public String getBaseUrl() { return baseUrl; } public void setBaseUrl(String value) { baseUrl = value; }
    public String getApiKey() { return apiKey; } public void setApiKey(String value) { apiKey = value; }
    public String getTextModel() { return textModel; } public void setTextModel(String value) { textModel = value; }
    public String getVisionModel() { return visionModel; } public void setVisionModel(String value) { visionModel = value; }
    public int getProposalMinutes() { return proposalMinutes; } public void setProposalMinutes(int value) { proposalMinutes = value; }
    public int getMaxInstructionLength() { return maxInstructionLength; } public void setMaxInstructionLength(int value) { maxInstructionLength = value; }
}
