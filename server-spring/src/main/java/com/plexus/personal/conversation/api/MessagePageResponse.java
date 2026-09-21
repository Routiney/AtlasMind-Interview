package com.plexus.personal.conversation.api;

import com.plexus.personal.conversation.domain.model.Message;

import java.util.List;

public record MessagePageResponse(
        List<MessageResponse> items,
        int page,
        int size,
        boolean hasNext
) {
    public static MessagePageResponse from(List<Message> messages, int page, int size) {
        boolean hasNext = messages.size() > size;
        List<Message> visible = hasNext ? messages.subList(0, size) : messages;
        return new MessagePageResponse(visible.stream().map(MessageResponse::from).toList(), page, size, hasNext);
    }
}
