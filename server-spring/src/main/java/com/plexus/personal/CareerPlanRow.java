package com.plexus.personal;

import java.time.OffsetDateTime;

public class CareerPlanRow {
    private Long id;
    private Long userId;
    private String requestId;
    private String targetRole;
    private String jobDescription;
    private Integer weeks;
    private Integer hoursPerWeek;
    private boolean researchMarket;
    private String resultJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }
    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
    public Integer getWeeks() { return weeks; }
    public void setWeeks(Integer weeks) { this.weeks = weeks; }
    public Integer getHoursPerWeek() { return hoursPerWeek; }
    public void setHoursPerWeek(Integer hoursPerWeek) { this.hoursPerWeek = hoursPerWeek; }
    public boolean isResearchMarket() { return researchMarket; }
    public void setResearchMarket(boolean researchMarket) { this.researchMarket = researchMarket; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
