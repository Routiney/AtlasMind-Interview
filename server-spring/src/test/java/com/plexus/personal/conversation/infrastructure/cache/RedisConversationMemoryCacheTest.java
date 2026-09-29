package com.plexus.personal.conversation.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plexus.personal.conversation.domain.model.ConversationMemory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class RedisConversationMemoryCacheTest {
    @Autowired
    private RedisConversationMemoryCache cache;

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
    void storesConversationMemoryWithUserScopedKeyAndTtl() {
        ConversationMemory memory = new ConversationMemory(
                9001L,
                "用户正在准备 Java 后端面试",
                objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                        .put("key", "target_role")
                        .put("value", "Java 后端")
                        .put("source", "user")),
                3L,
                0L,
                12L,
                OffsetDateTime.now()
        );

        cache.put(101L, 9001L, memory);

        Optional<ConversationMemory> loaded = cache.get(101L, 9001L);
        Long ttl = redis.getExpire(cache.key(101L, 9001L), TimeUnit.SECONDS);

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().summary()).isEqualTo("用户正在准备 Java 后端面试");
        assertThat(loaded.orElseThrow().version()).isEqualTo(3L);
        assertThat(ttl).isBetween(1L, Duration.ofHours(24).getSeconds());
        assertThat(cache.get(202L, 9001L)).isEmpty();
    }

    @Test
    void evictsConversationMemoryExplicitly() {
        ConversationMemory memory = new ConversationMemory(
                9001L, "summary", objectMapper.createArrayNode(), 1L, 0L, 0L, OffsetDateTime.now());

        cache.put(101L, 9001L, memory);
        cache.evict(101L, 9001L);

        assertThat(cache.get(101L, 9001L)).isEmpty();
    }
}
