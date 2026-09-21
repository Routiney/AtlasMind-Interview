package com.plexus.personal.conversation.application;

public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException(Long conversationId) {
        super("Conversation not found: " + conversationId);
    }
}
