package com.plexus.personal.user.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserMapper {

    Optional<UserRow> findByUsername(@Param("username") String username);

    int insert(
            @Param("username") String username,
            @Param("passwordHash") String passwordHash,
            @Param("displayName") String displayName
    );
}
