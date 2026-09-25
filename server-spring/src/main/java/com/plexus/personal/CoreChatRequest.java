package com.plexus.personal;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record CoreChatRequest(
        String query,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("conversation_id") Long conversationId,
        boolean stream,
        @JsonProperty("resume_profile") Map<String, String> resumeProfile,
        @JsonProperty("deep_thinking") boolean deepThinking,
        List<HistoryMessage> history,
        Map<String, Object> memory
) {
    public record HistoryMessage(String role, String content) {}
}
