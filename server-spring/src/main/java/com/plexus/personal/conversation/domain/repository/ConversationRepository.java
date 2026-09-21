package com.plexus.personal.conversation.domain.repository;

import com.plexus.personal.conversation.domain.model.Conversation;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository {

    Conversation create(Long userId, String title);

    Optional<Conversation> findById(Long conversationId);

    List<Conversation> findByUserId(Long userId, int limit, int offset);

    Conversation updateTitle(Long conversationId, String title);

    void delete(Long conversationId);
}
