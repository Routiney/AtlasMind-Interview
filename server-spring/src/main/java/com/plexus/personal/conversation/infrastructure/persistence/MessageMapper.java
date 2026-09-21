package com.plexus.personal.conversation.infrastructure.persistence;

import com.plexus.personal.conversation.domain.model.MessageRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper {

    Long insert(
            @Param("conversationId") Long conversationId,
            @Param("role") MessageRole role,
            @Param("content") String content
    );

    List<MessageRow> findByConversationId(@Param("conversationId") Long conversationId, @Param("limit") int limit, @Param("offset") int offset);
}
