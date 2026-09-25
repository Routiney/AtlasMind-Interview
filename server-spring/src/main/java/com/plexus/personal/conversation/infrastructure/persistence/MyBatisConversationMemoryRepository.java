package com.plexus.personal.conversation.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plexus.personal.conversation.domain.model.ConversationMemory;
import com.plexus.personal.conversation.domain.repository.ConversationMemoryRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisConversationMemoryRepository implements ConversationMemoryRepository {

    private final ConversationMemoryMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisConversationMemoryRepository(ConversationMemoryMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ConversationMemory> findByConversationId(Long conversationId) {
        ConversationMemoryRow row = mapper.findByConversationId(conversationId);
        if (row == null) return Optional.empty();
        JsonNode facts;
        try {
            facts = objectMapper.readTree(row.getFactsJson());
        } catch (Exception exception) {
            facts = objectMapper.createArrayNode();
        }
        return Optional.of(new ConversationMemory(row.getConversationId(), row.getSummary(), facts,
                row.getVersion(), row.getForgottenBefore(), row.getCoveredUntilMessageId(), row.getUpdatedAt()));
    }

    @Override
    public void ensure(Long conversationId) {
        mapper.ensure(conversationId);
    }

    @Override
    public boolean update(Long conversationId, long version, String summary, String factsJson, long coveredUntilMessageId) {
        return mapper.update(conversationId, version, summary, factsJson, coveredUntilMessageId) == 1;
    }

    @Override
    public void clear(Long conversationId) {
        mapper.clear(conversationId);
    }
}
