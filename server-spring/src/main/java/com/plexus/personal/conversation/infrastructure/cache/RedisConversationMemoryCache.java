package com.plexus.personal.conversation.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plexus.personal.conversation.domain.model.ConversationMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Hot cache for the compact conversation memory used by ChatService.
 * PostgreSQL remains the durable source of truth; Redis failures are cache misses.
 */
@Component
public class RedisConversationMemoryCache {
    private static final Logger log = LoggerFactory.getLogger(RedisConversationMemoryCache.class);
    private static final String KEY_PREFIX = "atlas:chat-memory:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisConversationMemoryCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${plexus.chat.memory-cache-ttl:24h}") Duration ttl
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    public Optional<ConversationMemory> get(Long userId, Long conversationId) {
        String value;
        try {
            value = redis.opsForValue().get(key(userId, conversationId));
        } catch (RuntimeException exception) {
            log.debug("Conversation memory cache read failed: {}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(value, ConversationMemory.class));
        } catch (Exception exception) {
            log.warn("Ignoring malformed conversation memory cache entry for conversation {}", conversationId);
            evict(userId, conversationId);
            return Optional.empty();
        }
    }

    public void put(Long userId, Long conversationId, ConversationMemory memory) {
        try {
            String value = objectMapper.writeValueAsString(memory);
            redis.opsForValue().set(key(userId, conversationId), value, ttl);
        } catch (Exception exception) {
            log.debug("Conversation memory cache write failed: {}", exception.getClass().getSimpleName());
        }
    }

    public void evict(Long userId, Long conversationId) {
        try {
            redis.delete(key(userId, conversationId));
        } catch (RuntimeException exception) {
            log.debug("Conversation memory cache eviction failed: {}", exception.getClass().getSimpleName());
        }
    }

    public String key(Long userId, Long conversationId) {
        if (userId == null || userId <= 0 || conversationId == null || conversationId <= 0) {
            throw new IllegalArgumentException("userId and conversationId must be positive");
        }
        return KEY_PREFIX + userId + ":" + conversationId;
    }
}
