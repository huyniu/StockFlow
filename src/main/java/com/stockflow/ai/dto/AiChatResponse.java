package com.stockflow.ai.dto;

public class AiChatResponse {
    private String reply;
    private boolean aiGenerated;

    public AiChatResponse() {}

    public AiChatResponse(String reply, boolean aiGenerated) {
        this.reply = reply;
        this.aiGenerated = aiGenerated;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public boolean isAiGenerated() {
        return aiGenerated;
    }

    public void setAiGenerated(boolean aiGenerated) {
        this.aiGenerated = aiGenerated;
    }
}