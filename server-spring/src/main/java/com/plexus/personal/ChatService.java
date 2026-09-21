package com.plexus.personal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plexus.personal.conversation.application.ConversationService;
import com.plexus.personal.conversation.domain.model.Conversation;
import com.plexus.personal.conversation.domain.model.MessageRole;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

@Service
public class ChatService {

    private final ConversationService conversationService;
    private final AgentService agentService;
    private final ObjectMapper objectMapper;

    public ChatService(
            ConversationService conversationService,
            AgentService agentService,
            ObjectMapper objectMapper
    ) {
        this.conversationService = conversationService;
        this.agentService = agentService;
        this.objectMapper = objectMapper;
    }

    public Flux<DataBuffer> execute(Long userId, ChatRequest request) {
        Conversation conversation = conversationService.resolveForChat(
                userId,
                request.conversationId(),
                titleFrom(request.query())
        );
        conversationService.appendMessage(userId, conversation.id(), MessageRole.USER, request.query());

        FinalEventCapture capture = new FinalEventCapture();
        return agentService.execute(request.withConversationId(conversation.id()))
                .doOnNext(buffer -> capture.accept(buffer.toString(StandardCharsets.UTF_8)))
                .doOnComplete(() -> {
                    if (capture.finalContent != null) {
                        conversationService.appendMessage(
                                userId,
                                conversation.id(),
                                MessageRole.ASSISTANT,
                                capture.finalContent
                        );
                    }
                });
    }

    private String titleFrom(String query) {
        return query.length() <= 200 ? query : query.substring(0, 200);
    }

    private final class FinalEventCapture {
        private final StringBuilder pending = new StringBuilder();
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
            if (!"final".equals(eventName) || data == null || data.isBlank()) {
                return;
            }
            try {
                JsonNode payload = objectMapper.readTree(data);
                JsonNode content = payload.get("content");
                if (content != null && content.isTextual()) {
                    finalContent = content.asText();
                }
            } catch (Exception ignored) {
                // A malformed final event should not break the already active SSE stream.
            }
        }
    }
}
