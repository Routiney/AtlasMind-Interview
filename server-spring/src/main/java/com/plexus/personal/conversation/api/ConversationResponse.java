package com.plexus.personal.conversation.api;

import com.plexus.personal.conversation.domain.model.Conversation;

import java.time.OffsetDateTime;

public record ConversationResponse(
        Long id,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(
                conversation.id(),
                conversation.title(),
                conversation.createdAt(),
                conversation.updatedAt()
        );
    }
}
