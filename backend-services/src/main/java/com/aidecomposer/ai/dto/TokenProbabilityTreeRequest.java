package com.aidecomposer.ai.dto;

public class TokenProbabilityTreeRequest {
    private String prompt;
    private Double temperature = 0.7;
    private Double topP = 0.9;

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }
}

