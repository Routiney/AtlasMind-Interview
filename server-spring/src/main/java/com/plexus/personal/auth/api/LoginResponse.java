package com.plexus.personal.auth.api;

public record LoginResponse(
        String tokenType,
        String accessToken,
        long expiresIn
) {
}
