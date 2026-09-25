package com.plexus.personal.conversation.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record ConversationMemory(
        Long conversationId,
        String summary,
        JsonNode facts,
        long version,
        long forgottenBefore,
        long coveredUntilMessageId,
        OffsetDateTime updatedAt
) {
}
