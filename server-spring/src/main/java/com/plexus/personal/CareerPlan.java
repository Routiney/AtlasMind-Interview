package com.plexus.personal;

import java.time.OffsetDateTime;

public record CareerPlan(
        Long id,
        Long userId,
        String requestId,
        String targetRole,
        String jobDescription,
        Integer weeks,
        Integer hoursPerWeek,
        boolean researchMarket,
        String resultJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
