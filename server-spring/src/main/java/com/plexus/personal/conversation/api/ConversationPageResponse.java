package com.plexus.personal.conversation.api;

import com.plexus.personal.conversation.domain.model.Conversation;

import java.util.List;

public record ConversationPageResponse(
        List<ConversationResponse> items,
        int page,
        int size,
        boolean hasNext
) {
    public static ConversationPageResponse from(List<Conversation> conversations, int page, int size) {
        boolean hasNext = conversations.size() > size;
        List<Conversation> visible = hasNext ? conversations.subList(0, size) : conversations;
        return new ConversationPageResponse(
                visible.stream().map(ConversationResponse::from).toList(),
                page,
                size,
                hasNext
        );
    }
}
