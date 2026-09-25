package com.plexus.personal.conversation.domain.repository;

import com.plexus.personal.conversation.domain.model.ConversationMemory;

import java.util.Optional;

public interface ConversationMemoryRepository {
    Optional<ConversationMemory> findByConversationId(Long conversationId);

    void ensure(Long conversationId);

    boolean update(Long conversationId, long version, String summary, String factsJson, long coveredUntilMessageId);

    void clear(Long conversationId);
}
