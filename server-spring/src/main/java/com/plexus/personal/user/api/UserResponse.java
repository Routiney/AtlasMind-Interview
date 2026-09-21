package com.plexus.personal.user.api;

import com.plexus.personal.user.domain.model.User;

import java.time.OffsetDateTime;

public record UserResponse(
        Long id,
        String username,
        String displayName,
        boolean enabled,
        OffsetDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.id(),
                user.username(),
                user.displayName(),
                user.enabled(),
                user.createdAt()
        );
    }
}
