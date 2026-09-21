package com.plexus.personal.conversation.api;

import com.plexus.personal.conversation.domain.model.Message;
import com.plexus.personal.conversation.domain.model.MessageRole;

import java.time.OffsetDateTime;

public record MessageResponse(
        Long id,
        MessageRole role,
        String content,
        OffsetDateTime createdAt
) {
    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.id(),
                message.role(),
                message.content(),
                message.createdAt()
        );
    }
}
