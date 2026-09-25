package com.plexus.personal;

import java.time.OffsetDateTime;

public record CareerPlanSummaryResponse(
        Long id,
        String targetRole,
        String jobDescription,
        Integer weeks,
        Integer hoursPerWeek,
        boolean researchMarket,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CareerPlanSummaryResponse from(CareerPlan plan) {
        return new CareerPlanSummaryResponse(plan.id(), plan.targetRole(), plan.jobDescription(), plan.weeks(), plan.hoursPerWeek(), plan.researchMarket(), plan.createdAt(), plan.updatedAt());
    }
}
