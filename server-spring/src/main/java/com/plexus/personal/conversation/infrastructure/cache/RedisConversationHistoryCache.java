package com.plexus.personal.conversation.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plexus.personal.conversation.domain.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Redis List cache for the recent conversation transcript used to build Agent history.
 * List index 0 is the newest message, matching the existing SQL query order.
 */
@Component
public class RedisConversationHistoryCache {
    private static final Logger log = LoggerFactory.getLogger(RedisConversationHistoryCache.class);
    private static final String KEY_PREFIX = "atlas:chat-history:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisConversationHistoryCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${plexus.chat.history-cache-ttl:7d}") Duration ttl
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    public Optional<List<Message>> get(Long userId, Long conversationId) {
        String key = key(userId, conversationId);
        try {
            if (!Boolean.TRUE.equals(redis.hasKey(key))) return Optional.empty();
            List<String> values = redis.opsForList().range(key, 0, -1);
            if (values == null) return Optional.empty();
            List<Message> messages = new ArrayList<>(values.size());
            for (String value : values) messages.add(objectMapper.readValue(value, Message.class));
            return Optional.of(messages);
        } catch (Exception exception) {
            log.debug("Conversation history cache read failed: {}", exception.getClass().getSimpleName());
            evict(userId, conversationId);
            return Optional.empty();
        }
    }

    public void replace(Long userId, Long conversationId, List<Message> newestFirst) {
        String key = key(userId, conversationId);
        try {
            redis.delete(key);
            if (newestFirst == null || newestFirst.isEmpty()) return;
            List<String> values = new ArrayList<>(newestFirst.size());
            for (Message message : newestFirst) values.add(objectMapper.writeValueAsString(message));
            redis.opsForList().rightPushAll(key, values);
            redis.expire(key, ttl);
        } catch (Exception exception) {
            log.debug("Conversation history cache replace failed: {}", exception.getClass().getSimpleName());
            evict(userId, conversationId);
        }
    }

    public void prepend(Long userId, Long conversationId, Message message) {
        String key = key(userId, conversationId);
        try {
            redis.opsForList().leftPush(key, objectMapper.writeValueAsString(message));
            redis.opsForList().trim(key, 0, 999);
            redis.expire(key, ttl);
        } catch (Exception exception) {
            log.debug("Conversation history cache append failed: {}", exception.getClass().getSimpleName());
            evict(userId, conversationId);
        }
    }

    public void evict(Long userId, Long conversationId) {
        try {
            redis.delete(key(userId, conversationId));
        } catch (RuntimeException exception) {
            log.debug("Conversation history cache eviction failed: {}", exception.getClass().getSimpleName());
        }
    }

    public String key(Long userId, Long conversationId) {
        if (userId == null || userId <= 0 || conversationId == null || conversationId <= 0) {
            throw new IllegalArgumentException("userId and conversationId must be positive");
        }
        return KEY_PREFIX + userId + ":" + conversationId;
    }
}
