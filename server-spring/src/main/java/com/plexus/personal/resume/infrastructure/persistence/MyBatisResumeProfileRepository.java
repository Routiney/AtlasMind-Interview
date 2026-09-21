package com.plexus.personal.resume.infrastructure.persistence;

import com.plexus.personal.resume.domain.model.ResumeProfile;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisResumeProfileRepository {
    private final ResumeProfileMapper mapper;

    public MyBatisResumeProfileRepository(ResumeProfileMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<ResumeProfile> findByUserId(Long userId) {
        return mapper.findByUserId(userId).map(this::toDomain);
    }

    public ResumeProfile upsert(Long userId, String name, String targetRole, String summary, String education, String internship, String projects, String skills) {
        int rows = mapper.upsert(userId, name, targetRole, summary, education, internship, projects, skills);
        if (rows != 1) throw new IllegalStateException("Expected one resume profile row but got " + rows);
        return findByUserId(userId).orElseThrow(() -> new IllegalStateException("Saved resume profile could not be loaded"));
    }

    private ResumeProfile toDomain(ResumeProfileRow row) {
        return new ResumeProfile(row.getId(), row.getUserId(), row.getName(), row.getTargetRole(), row.getSummary(), row.getEducation(), row.getInternship(), row.getProjects(), row.getSkills(), row.getCreatedAt(), row.getUpdatedAt());
    }
}
