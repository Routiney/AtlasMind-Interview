package com.plexus.personal;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank(message = "query 不能为空")
        String query,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("conversation_id") Long conversationId,
        boolean stream
) {
    public ChatRequest withConversationId(Long id) {
        return new ChatRequest(query, sessionId, id, stream);
    }
}
