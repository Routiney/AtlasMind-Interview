package com.plexus.personal.conversation.domain.model;

import java.time.OffsetDateTime;

public record Message(
        Long id,
        Long conversationId,
        MessageRole role,
        String content,
        OffsetDateTime createdAt
) {
}
