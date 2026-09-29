package com.plexus.personal.conversation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.plexus.personal.conversation.infrastructure.cache.RedisConversationMemoryCache;
import com.plexus.personal.conversation.domain.model.ConversationMemory;
import com.plexus.personal.conversation.domain.repository.ConversationMemoryRepository;
import org.springframework.stereotype.Service;

@Service
public class ConversationMemoryService {

    private final ConversationMemoryRepository repository;
    private final ConversationService conversations;
    private final RedisConversationMemoryCache cache;

    public ConversationMemoryService(
            ConversationMemoryRepository repository,
            ConversationService conversations,
            RedisConversationMemoryCache cache
    ) {
        this.repository = repository;
        this.conversations = conversations;
        this.cache = cache;
    }

    public ConversationMemory read(Long userId, Long conversationId) {
        conversations.requireOwnedConversation(userId, conversationId);
        repository.ensure(conversationId);
        return cache.get(userId, conversationId)
                .orElseGet(() -> {
                    ConversationMemory memory = repository.findByConversationId(conversationId).orElseThrow();
                    cache.put(userId, conversationId, memory);
                    return memory;
                });
    }

    public boolean update(Long userId, ConversationMemory previous, JsonNode payload, long coveredUntilMessageId) {
        conversations.requireOwnedConversation(userId, previous.conversationId());
        if (payload == null || !payload.path("summary").isTextual() || !payload.path("facts").isArray()) return false;
        String summary = payload.get("summary").asText();
        JsonNode facts = payload.get("facts");
        if (summary.length() > 4000 || facts.size() > 30 || facts.toString().length() > 20000) return false;
        for (JsonNode fact : facts) {
            if (!fact.path("key").isTextual() || !fact.path("value").isTextual()
                    || !("user".equals(fact.path("source").asText())
                    || "conversation".equals(fact.path("source").asText()))) return false;
        }
        boolean updated = repository.update(previous.conversationId(), previous.version(), summary, facts.toString(), coveredUntilMessageId);
        if (updated) {
            repository.findByConversationId(previous.conversationId())
                    .ifPresent(memory -> cache.put(userId, previous.conversationId(), memory));
        } else {
            cache.evict(userId, previous.conversationId());
        }
        return updated;
    }

    public void clear(Long userId, Long conversationId) {
        read(userId, conversationId);
        repository.clear(conversationId);
        cache.evict(userId, conversationId);
    }
}
