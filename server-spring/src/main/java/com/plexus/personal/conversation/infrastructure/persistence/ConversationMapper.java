package com.plexus.personal.conversation.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ConversationMapper {

    Long insert(@Param("userId") Long userId, @Param("title") String title);

    Optional<ConversationRow> findById(@Param("conversationId") Long conversationId);

    List<ConversationRow> findByUserId(@Param("userId") Long userId, @Param("limit") int limit, @Param("offset") int offset);

    int updateTitle(@Param("conversationId") Long conversationId, @Param("title") String title);

    int delete(@Param("conversationId") Long conversationId);
}
