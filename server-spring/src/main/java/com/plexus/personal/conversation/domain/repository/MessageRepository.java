package com.plexus.personal.conversation.domain.repository;

import com.plexus.personal.conversation.domain.model.Message;
import com.plexus.personal.conversation.domain.model.MessageRole;

import java.util.List;

public interface MessageRepository {

    Message append(Long conversationId, MessageRole role, String content);

    List<Message> findByConversationId(Long conversationId, int limit, int offset);
}
