package com.plexus.personal.conversation.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plexus.personal.conversation.domain.model.Message;
import com.plexus.personal.conversation.domain.model.MessageRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class RedisConversationHistoryCacheTest {
    @Autowired
    private RedisConversationHistoryCache cache;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        redis.delete(cache.key(101L, 9001L));
        redis.delete(cache.key(202L, 9001L));
    }

    @Test
    void storesNewestFirstMessagesInAUserScopedListWithTtl() {
        Message assistant = new Message(2L, 9001L, MessageRole.ASSISTANT, "准备 Spring 面试。", OffsetDateTime.now());
        Message user = new Message(1L, 9001L, MessageRole.USER, "我准备 Java 后端面试。", OffsetDateTime.now());

        cache.replace(101L, 9001L, List.of(assistant, user));

        Optional<List<Message>> loaded = cache.get(101L, 9001L);
        Long ttl = redis.getExpire(cache.key(101L, 9001L), TimeUnit.SECONDS);

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow()).extracting(Message::id).containsExactly(2L, 1L);
        assertThat(ttl).isBetween(1L, Duration.ofDays(7).getSeconds());
        assertThat(cache.get(202L, 9001L)).isEmpty();
    }

    @Test
    void prependsNewMessagesAndCanEvictTheList() {
        Message oldMessage = new Message(1L, 9001L, MessageRole.USER, "第一轮", OffsetDateTime.now());
        Message newMessage = new Message(2L, 9001L, MessageRole.ASSISTANT, "第二轮", OffsetDateTime.now());

        cache.replace(101L, 9001L, List.of(oldMessage));
        cache.prepend(101L, 9001L, newMessage);

        assertThat(cache.get(101L, 9001L).orElseThrow()).extracting(Message::id).containsExactly(2L, 1L);
        cache.evict(101L, 9001L);
        assertThat(cache.get(101L, 9001L)).isEmpty();
    }
}
