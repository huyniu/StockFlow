package com.stockflow.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {
    public static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";
    private String apiKey = "";
    private String model = DEFAULT_MODEL;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model != null && !model.isBlank() ? model.trim() : DEFAULT_MODEL;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
