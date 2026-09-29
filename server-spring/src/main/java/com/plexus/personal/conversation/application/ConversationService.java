package com.plexus.personal.conversation.application;

import com.plexus.personal.conversation.domain.model.Conversation;
import com.plexus.personal.conversation.domain.model.Message;
import com.plexus.personal.conversation.domain.repository.ConversationRepository;
import com.plexus.personal.conversation.domain.repository.MessageRepository;
import com.plexus.personal.conversation.infrastructure.cache.RedisConversationMemoryCache;
import com.plexus.personal.conversation.infrastructure.cache.RedisConversationHistoryCache;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final RedisConversationMemoryCache memoryCache;
    private final RedisConversationHistoryCache historyCache;

    public ConversationService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            RedisConversationMemoryCache memoryCache,
            RedisConversationHistoryCache historyCache
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.memoryCache = memoryCache;
        this.historyCache = historyCache;
    }

    public Conversation create(Long userId, String title) {
        return conversationRepository.create(userId, title);
    }

    public List<Conversation> findForUser(Long userId, int limit, int offset) {
        return conversationRepository.findByUserId(userId, limit, offset);
    }

    public Conversation updateTitle(Long userId, Long conversationId, String title) {
        requireOwnedConversation(userId, conversationId);
        return conversationRepository.updateTitle(conversationId, title.trim());
    }

    public void delete(Long userId, Long conversationId) {
        requireOwnedConversation(userId, conversationId);
        conversationRepository.delete(conversationId);
        memoryCache.evict(userId, conversationId);
        historyCache.evict(userId, conversationId);
    }

    public List<Message> findMessages(Long userId, Long conversationId, int limit, int offset) {
        requireOwnedConversation(userId, conversationId);
        return messageRepository.findByConversationId(conversationId, limit, offset);
    }

    /**
     * History path used by ChatService. It avoids a PostgreSQL read after the Redis
     * list has been warmed, while the normal paginated API keeps using SQL.
     */
    public List<Message> findMessagesForChatContext(Long userId, Long conversationId) {
        requireOwnedConversation(userId, conversationId);
        return historyCache.get(userId, conversationId)
                .orElseGet(() -> {
                    List<Message> messages = messageRepository.findByConversationId(conversationId, 1000, 0);
                    historyCache.replace(userId, conversationId, messages);
                    return messages;
                });
    }

    public List<Message> findMessagesAfter(Long userId, Long conversationId, long messageId, int limit) {
        requireOwnedConversation(userId, conversationId);
        return messageRepository.findAfterId(conversationId, messageId, limit);
    }

    public Conversation resolveForChat(Long userId, Long conversationId, String title) {
        if (conversationId == null) {
            return create(userId, title);
        }
        return requireOwnedConversation(userId, conversationId);
    }

    public Message appendMessage(Long userId, Long conversationId,
                                 com.plexus.personal.conversation.domain.model.MessageRole role,
                                 String content) {
        requireOwnedConversation(userId, conversationId);
        Message message = messageRepository.append(conversationId, role, content);
        historyCache.prepend(userId, conversationId, message);
        return message;
    }

    public Conversation requireOwnedConversation(Long userId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
        if (!conversation.userId().equals(userId)) {
            throw new ConversationNotFoundException(conversationId);
        }
        return conversation;
    }
}
