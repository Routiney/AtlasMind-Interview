package com.plexus.personal;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PlanRequest(
        @jakarta.validation.constraints.Size(max = 64, message = "request_id 不能超过 64 个字符")
        @JsonProperty("request_id") String requestId,
        @jakarta.validation.constraints.Size(max = 8000, message = "job_description 不能超过 8000 个字符")
        @JsonProperty("job_description") String jobDescription,
        @Min(value = 1, message = "weeks 必须在 1 到 24 之间")
        @Max(value = 24, message = "weeks 必须在 1 到 24 之间")
        Integer weeks,
        @JsonProperty("hours_per_week")
        @Min(value = 1, message = "hours_per_week 必须在 1 到 40 之间")
        @Max(value = 40, message = "hours_per_week 必须在 1 到 40 之间")
        Integer hoursPerWeek,
        @JsonProperty("research_market") boolean researchMarket
) {
}
