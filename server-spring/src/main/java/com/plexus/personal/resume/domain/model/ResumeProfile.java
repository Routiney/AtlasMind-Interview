package com.plexus.personal.resume.domain.model;

import java.time.OffsetDateTime;

public record ResumeProfile(
        Long id,
        Long userId,
        String name,
        String targetRole,
        String summary,
        String education,
        String internship,
        String projects,
        String skills,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
