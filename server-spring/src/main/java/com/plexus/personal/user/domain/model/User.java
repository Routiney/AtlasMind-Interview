package com.plexus.personal.user.domain.model;

import java.time.OffsetDateTime;

public record User(
        Long id,
        String username,
        String passwordHash,
        String displayName,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
