package com.plexus.personal;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record MemoryCoreRequest(
        Map<String, Object> memory,
        List<ContextMessage> context
) {
    public record ContextMessage(
            String role,
            String content,
            @JsonProperty("message_id") long messageId
    ) {}
}
