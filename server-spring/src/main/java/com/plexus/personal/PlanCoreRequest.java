package com.plexus.personal;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record PlanCoreRequest(
        @JsonProperty("resume_profile") Map<String, String> resumeProfile,
        @JsonProperty("job_description") String jobDescription,
        Integer weeks,
        @JsonProperty("hours_per_week") Integer hoursPerWeek,
        @JsonProperty("research_market") boolean researchMarket
) {
}
