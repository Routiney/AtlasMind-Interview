package com.plexus.personal.conversation.infrastructure.persistence;

import com.plexus.personal.conversation.domain.model.Message;
import com.plexus.personal.conversation.domain.model.MessageRole;
import com.plexus.personal.conversation.domain.repository.MessageRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MyBatisMessageRepository implements MessageRepository {

    private final MessageMapper messageMapper;

    public MyBatisMessageRepository(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    @Override
    public Message append(Long conversationId, MessageRole role, String content) {
        Long messageId = messageMapper.insert(conversationId, role, content);
        return findByConversationId(conversationId, 1, 0).stream()
                .filter(message -> message.id().equals(messageId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Created message could not be loaded"));
    }

    @Override
    public List<Message> findByConversationId(Long conversationId, int limit, int offset) {
        return messageMapper.findByConversationId(conversationId, limit, offset).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Message> findAfterId(Long conversationId, long messageId, int limit) {
        return messageMapper.findAfterId(conversationId, messageId, limit).stream()
                .map(this::toDomain)
                .toList();
    }

    private Message toDomain(MessageRow row) {
        return new Message(
                row.getId(),
                row.getConversationId(),
                row.getRole(),
                row.getContent(),
                row.getCreatedAt()
        );
    }
}
