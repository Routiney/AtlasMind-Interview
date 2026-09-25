package com.plexus.personal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plexus.personal.conversation.application.ConversationMemoryService;
import com.plexus.personal.conversation.application.ConversationMemoryRefreshService;
import com.plexus.personal.conversation.application.ConversationService;
import com.plexus.personal.conversation.domain.model.Conversation;
import com.plexus.personal.conversation.domain.model.ConversationMemory;
import com.plexus.personal.conversation.domain.model.Message;
import com.plexus.personal.conversation.domain.model.MessageRole;
import com.plexus.personal.resume.application.ResumeProfileService;
import com.plexus.personal.resume.domain.model.ResumeProfile;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ChatService {

    private final ConversationService conversationService;
    private final AgentService agentService;
    private final ResumeProfileService resumeProfileService;
    private final ConversationMemoryService conversationMemoryService;
    private final ConversationMemoryRefreshService memoryRefreshService;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, ReentrantLock> conversationLocks = new ConcurrentHashMap<>();
    private final int contextTokenBudget;

    public ChatService(
            ConversationService conversationService,
            AgentService agentService,
            ResumeProfileService resumeProfileService,
            ConversationMemoryService conversationMemoryService,
            ConversationMemoryRefreshService memoryRefreshService,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${plexus.chat.context-token-budget:6000}") int contextTokenBudget
    ) {
        this.conversationService = conversationService;
        this.agentService = agentService;
        this.resumeProfileService = resumeProfileService;
        this.conversationMemoryService = conversationMemoryService;
        this.memoryRefreshService = memoryRefreshService;
        this.objectMapper = objectMapper;
        this.contextTokenBudget = contextTokenBudget;
    }

    public Flux<DataBuffer> execute(Long userId, ChatRequest request) {
        return Flux.defer(() -> {
            Conversation conversation = conversationService.resolveForChat(
                    userId, request.conversationId(), titleFrom(request.query()));
            ReentrantLock lock = conversationLocks.computeIfAbsent(conversation.id(), ignored -> new ReentrantLock());
            if (!lock.tryLock()) {
                return Flux.error(new ResponseStatusException(HttpStatus.CONFLICT,
                        "该会话正在生成回答，请稍后重试"));
            }
            try {
                ConversationMemory conversationMemory = conversationMemoryService.read(userId, conversation.id());
                List<Message> history = contextWindow(userId, conversation.id(), conversationMemory);
                conversationService.appendMessage(userId, conversation.id(), MessageRole.USER, request.query());
                Map<String, String> resumeProfile = request.includeResume()
                        ? resumeProfileService.findByUserId(userId).map(this::toResumeContext).orElse(null)
                        : null;
                CoreChatRequest coreRequest = new CoreChatRequest(
                        request.query(), request.sessionId(), conversation.id(), request.stream(), resumeProfile,
                        request.deepThinking(), history.stream()
                                .map(message -> new CoreChatRequest.HistoryMessage(message.role().name(), message.content()))
                                .toList(),
                        Map.of("summary", conversationMemory.summary(), "facts", conversationMemory.facts())
                );
                FinalEventCapture capture = new FinalEventCapture();
                AtomicBoolean assistantSaved = new AtomicBoolean();
                return agentService.execute(coreRequest)
                        .doOnNext(buffer -> capture.accept(buffer.toString(StandardCharsets.UTF_8)))
                        .onErrorResume(error -> Flux.just(errorEvent(conversation.id(), "模型服务暂时不可用，请稍后重试。")))
                        .doFinally(signalType -> {
                            capture.finish();
                            String content = capture.content();
                            if (content != null && !content.isBlank() && assistantSaved.compareAndSet(false, true)) {
                                conversationService.appendMessage(userId, conversation.id(), MessageRole.ASSISTANT, content);
                                memoryRefreshService.scheduleIfNeeded(userId, conversation.id());
                            }
                            lock.unlock();
                            conversationLocks.remove(conversation.id(), lock);
                        });
            } catch (RuntimeException exception) {
                lock.unlock();
                conversationLocks.remove(conversation.id(), lock);
                throw exception;
            }
        });
    }

    private DataBuffer errorEvent(Long conversationId, String message) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "message", message,
                    "conversation_id", conversationId
            ));
            return DefaultDataBufferFactory.sharedInstance.wrap(
                    ("event: error\ndata: " + payload + "\n\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            return DefaultDataBufferFactory.sharedInstance.wrap(
                    "event: error\ndata: {\"message\":\"模型服务暂时不可用\"}\n\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    private List<Message> contextWindow(Long userId, Long conversationId, ConversationMemory memory) {
        List<Message> newestFirst = conversationService.findMessages(userId, conversationId, 1000, 0);
        List<Message> selected = new ArrayList<>();
        int used = estimateTokens(memory.summary()) + estimateTokens(memory.facts().toString());
        int budget = Math.max(500, contextTokenBudget);
        for (Message message : newestFirst) {
            if (message.id() <= memory.forgottenBefore()) continue;
            int cost = estimateTokens(message.content()) + 4;
            if (!selected.isEmpty() && used + cost > budget) break;
            selected.add(message);
            used += cost;
        }
        Collections.reverse(selected);
        return selected;
    }

    private int estimateTokens(String content) {
        if (content == null || content.isBlank()) return 0;
        return Math.max(1, (content.codePointCount(0, content.length()) + 1) / 2);
    }

    private String titleFrom(String query) {
        return query.length() <= 200 ? query : query.substring(0, 200);
    }

    private Map<String, String> toResumeContext(ResumeProfile profile) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("name", profile.name());
        context.put("target_role", profile.targetRole());
        context.put("summary", profile.summary());
        context.put("education", profile.education());
        context.put("internship", profile.internship());
        context.put("projects", profile.projects());
        context.put("skills", profile.skills());
        return context;
    }

    private final class FinalEventCapture {
        private final StringBuilder pending = new StringBuilder();
        private final StringBuilder streamedContent = new StringBuilder();
        private String finalContent;

        private void accept(String chunk) {
            pending.append(chunk);
            int boundary;
            while ((boundary = pending.indexOf("\n\n")) >= 0) {
                String event = pending.substring(0, boundary);
                pending.delete(0, boundary + 2);
                readFinalEvent(event);
            }
        }

        private void finish() {
            if (pending.length() > 0) {
                readFinalEvent(pending.toString());
                pending.setLength(0);
            }
        }

        private void readFinalEvent(String event) {
            String eventName = null;
            String data = null;
            for (String line : event.split("\\r?\\n")) {
                if (line.startsWith("event:")) {
                    eventName = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    data = line.substring("data:".length()).trim();
                }
            }
            if ((!("final".equals(eventName) || "chunk".equals(eventName))) || data == null || data.isBlank()) {
                return;
            }
            try {
                JsonNode payload = objectMapper.readTree(data);
                JsonNode content = payload.get("content");
                if (content != null && content.isTextual()) {
                    if ("final".equals(eventName)) {
                        finalContent = content.asText();
                    } else {
                        streamedContent.append(content.asText());
                    }
                }
            } catch (Exception ignored) {
                // A malformed final event should not break the already active SSE stream.
            }
        }

        private String content() {
            return finalContent != null ? finalContent : streamedContent.toString();
        }
    }
}
