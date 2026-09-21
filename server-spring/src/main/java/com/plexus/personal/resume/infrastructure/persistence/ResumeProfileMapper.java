package com.plexus.personal.resume.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface ResumeProfileMapper {
    Optional<ResumeProfileRow> findByUserId(@Param("userId") Long userId);

    int upsert(
            @Param("userId") Long userId,
            @Param("name") String name,
            @Param("targetRole") String targetRole,
            @Param("summary") String summary,
            @Param("education") String education,
            @Param("internship") String internship,
            @Param("projects") String projects,
            @Param("skills") String skills
    );
}
