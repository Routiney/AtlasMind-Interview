package com.plexus.personal.conversation.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConversationMemoryMapper {
    ConversationMemoryRow findByConversationId(@Param("conversationId") Long conversationId);

    void ensure(@Param("conversationId") Long conversationId);

    void clear(@Param("conversationId") Long conversationId);

    int update(
            @Param("conversationId") Long conversationId,
            @Param("version") long version,
            @Param("summary") String summary,
            @Param("factsJson") String factsJson,
            @Param("coveredUntilMessageId") long coveredUntilMessageId
    );
}
