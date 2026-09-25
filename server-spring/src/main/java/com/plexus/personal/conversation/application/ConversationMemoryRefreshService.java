package com.plexus.personal.conversation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.plexus.personal.AgentService;
import com.plexus.personal.MemoryCoreRequest;
import com.plexus.personal.conversation.domain.model.ConversationMemory;
import com.plexus.personal.conversation.domain.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConversationMemoryRefreshService {
    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryRefreshService.class);
    private final ConversationService conversations;
    private final ConversationMemoryService memories;
    private final AgentService agent;
    private final Executor executor;
    private final Set<Long> scheduled = ConcurrentHashMap.newKeySet();
    private final Set<Long> pending = ConcurrentHashMap.newKeySet();
    private final int tokenThreshold;

    public ConversationMemoryRefreshService(
            ConversationService conversations,
            ConversationMemoryService memories,
            AgentService agent,
            @Qualifier("chatMemoryExecutor") Executor executor,
            @Value("${plexus.chat.memory-refresh-token-threshold:1200}") int tokenThreshold
    ) {
        this.conversations = conversations;
        this.memories = memories;
        this.agent = agent;
        this.executor = executor;
        this.tokenThreshold = tokenThreshold;
    }

    public void scheduleIfNeeded(Long userId, Long conversationId) {
        if (!scheduled.add(conversationId)) {
            pending.add(conversationId);
            return;
        }
        executor.execute(() -> {
            try {
                refreshIfNeeded(userId, conversationId);
            } finally {
                scheduled.remove(conversationId);
                if (pending.remove(conversationId)) {
                    scheduleIfNeeded(userId, conversationId);
                }
            }
        });
    }

    private void refreshIfNeeded(Long userId, Long conversationId) {
        try {
            ConversationMemory memory = memories.read(userId, conversationId);
            long cursor = Math.max(memory.coveredUntilMessageId(), memory.forgottenBefore());
            List<Message> delta = conversations.findMessagesAfter(userId, conversationId, cursor, 1000);
            long estimatedTokens = delta.stream().mapToLong(message -> estimateTokens(message.content()) + 4).sum();
            if (delta.isEmpty() || estimatedTokens < tokenThreshold) return;

            List<MemoryCoreRequest.ContextMessage> context = delta.stream()
                    .map(message -> new MemoryCoreRequest.ContextMessage(
                            message.role().name(), message.content(), message.id()))
                    .toList();
            JsonNode update = agent.memory(new MemoryCoreRequest(
                    java.util.Map.of("summary", memory.summary(), "facts", memory.facts()), context
            )).block(Duration.ofSeconds(75));
            if (update != null) {
                long coveredUntil = delta.get(delta.size() - 1).id();
                memories.update(userId, memory, update, coveredUntil);
            }
        } catch (Exception exception) {
            // A memory refresh is best-effort and must never affect the chat response.
            log.warn("Conversation memory refresh skipped for {}: {}", conversationId, exception.getClass().getSimpleName());
        }
    }

    private long estimateTokens(String content) {
        if (content == null || content.isBlank()) return 0;
        return Math.max(1, (content.codePointCount(0, content.length()) + 1) / 2);
    }
}
