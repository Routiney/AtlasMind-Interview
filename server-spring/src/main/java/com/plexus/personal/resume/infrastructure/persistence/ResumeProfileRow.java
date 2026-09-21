package com.plexus.personal.resume.infrastructure.persistence;

import java.time.OffsetDateTime;

public class ResumeProfileRow {
    private Long id;
    private Long userId;
    private String name;
    private String targetRole;
    private String summary;
    private String education;
    private String internship;
    private String projects;
    private String skills;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
    public String getInternship() { return internship; }
    public void setInternship(String internship) { this.internship = internship; }
    public String getProjects() { return projects; }
    public void setProjects(String projects) { this.projects = projects; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
