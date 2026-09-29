package com.plexus.personal.document.infrastructure.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RedisDocumentTaskStateService {
    private static final String PROGRESS_PREFIX = "atlas:document-progress:";
    private static final String DONE_PREFIX = "atlas:kafka:document-event-done:";

    private final StringRedisTemplate redis;
    private final Duration progressTtl;
    private final Duration deduplicationTtl;

    public RedisDocumentTaskStateService(
            StringRedisTemplate redis,
            @Value("${atlas.document.progress-ttl:24h}") Duration progressTtl,
            @Value("${atlas.document.event-dedup-ttl:7d}") Duration deduplicationTtl
    ) {
        this.redis = redis;
        this.progressTtl = progressTtl;
        this.deduplicationTtl = deduplicationTtl;
    }

    public void update(Long userId, Long documentId, String status, String stage,
                       int percent, String message) {
        String key = progressKey(userId, documentId);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("status", status);
        fields.put("stage", stage);
        fields.put("percent", Integer.toString(Math.max(0, Math.min(100, percent))));
        fields.put("message", message == null ? "" : message);
        fields.put("updatedAt", Instant.now().toString());
        redis.opsForHash().putAll(key, fields);
        redis.expire(key, progressTtl);
    }

    public boolean isDone(Long userId, String eventId) {
        return Boolean.TRUE.equals(redis.hasKey(doneKey(userId, eventId)));
    }

    public void markDone(Long userId, String eventId) {
        redis.opsForValue().set(doneKey(userId, eventId), "1", deduplicationTtl);
    }

    public String progressKey(Long userId, Long documentId) {
        validate(userId, documentId);
        return PROGRESS_PREFIX + userId + ":" + documentId;
    }

    private String doneKey(Long userId, String eventId) {
        if (userId == null || userId <= 0 || eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("userId and eventId must be valid");
        }
        return DONE_PREFIX + userId + ":" + eventId;
    }

    private void validate(Long userId, Long documentId) {
        if (userId == null || userId <= 0 || documentId == null || documentId <= 0) {
            throw new IllegalArgumentException("userId and documentId must be positive");
        }
    }
}
