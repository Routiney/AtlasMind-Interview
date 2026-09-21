package com.plexus.personal.resume.api;

import com.plexus.personal.resume.domain.model.ResumeProfile;

import java.time.OffsetDateTime;

public record ResumeProfileResponse(Long id, Long userId, String name, String targetRole, String summary, String education, String internship, String projects, String skills, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static ResumeProfileResponse from(ResumeProfile profile) {
        return new ResumeProfileResponse(profile.id(), profile.userId(), profile.name(), profile.targetRole(), profile.summary(), profile.education(), profile.internship(), profile.projects(), profile.skills(), profile.createdAt(), profile.updatedAt());
    }
}
