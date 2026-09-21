package com.plexus.personal.conversation.domain.model;

import java.time.OffsetDateTime;

public record Conversation(
        Long id,
        Long userId,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
