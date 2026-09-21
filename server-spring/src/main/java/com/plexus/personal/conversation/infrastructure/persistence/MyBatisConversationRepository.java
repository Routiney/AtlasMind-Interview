package com.plexus.personal.conversation.infrastructure.persistence;

import com.plexus.personal.conversation.domain.model.Conversation;
import com.plexus.personal.conversation.domain.repository.ConversationRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisConversationRepository implements ConversationRepository {

    private final ConversationMapper conversationMapper;

    public MyBatisConversationRepository(ConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
    }

    @Override
    public Conversation create(Long userId, String title) {
        Long conversationId = conversationMapper.insert(userId, title);
        return findById(conversationId)
                .orElseThrow(() -> new IllegalStateException("Created conversation could not be loaded"));
    }

    @Override
    public Optional<Conversation> findById(Long conversationId) {
        return conversationMapper.findById(conversationId).map(this::toDomain);
    }

    @Override
    public List<Conversation> findByUserId(Long userId, int limit, int offset) {
        return conversationMapper.findByUserId(userId, limit, offset).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Conversation updateTitle(Long conversationId, String title) {
        conversationMapper.updateTitle(conversationId, title);
        return findById(conversationId)
                .orElseThrow(() -> new IllegalStateException("Updated conversation could not be loaded"));
    }

    @Override
    public void delete(Long conversationId) {
        conversationMapper.delete(conversationId);
    }

    private Conversation toDomain(ConversationRow row) {
        return new Conversation(
                row.getId(),
                row.getUserId(),
                row.getTitle(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }
}
