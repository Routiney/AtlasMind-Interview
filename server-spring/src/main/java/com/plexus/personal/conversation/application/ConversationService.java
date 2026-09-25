package com.plexus.personal.conversation.application;

import com.plexus.personal.conversation.domain.model.Conversation;
import com.plexus.personal.conversation.domain.model.Message;
import com.plexus.personal.conversation.domain.repository.ConversationRepository;
import com.plexus.personal.conversation.domain.repository.MessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
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
    }

    public List<Message> findMessages(Long userId, Long conversationId, int limit, int offset) {
        requireOwnedConversation(userId, conversationId);
        return messageRepository.findByConversationId(conversationId, limit, offset);
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
        return messageRepository.append(conversationId, role, content);
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
