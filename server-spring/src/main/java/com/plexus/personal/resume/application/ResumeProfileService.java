package com.plexus.personal.resume.application;

import com.plexus.personal.resume.domain.model.ResumeProfile;
import com.plexus.personal.resume.infrastructure.persistence.MyBatisResumeProfileRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ResumeProfileService {
    private final MyBatisResumeProfileRepository repository;

    public ResumeProfileService(MyBatisResumeProfileRepository repository) {
        this.repository = repository;
    }

    public Optional<ResumeProfile> findByUserId(Long userId) { return repository.findByUserId(userId); }

    public ResumeProfile save(Long userId, ResumeProfileRequest request) {
        return repository.upsert(userId, request.name().trim(), request.targetRole().trim(), text(request.summary()), text(request.education()), text(request.internship()), text(request.projects()), text(request.skills()));
    }

    private String text(String value) { return value == null ? "" : value.trim(); }
}
