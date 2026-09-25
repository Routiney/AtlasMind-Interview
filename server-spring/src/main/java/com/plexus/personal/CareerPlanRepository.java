package com.plexus.personal;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CareerPlanRepository {
    private final CareerPlanMapper mapper;

    public CareerPlanRepository(CareerPlanMapper mapper) {
        this.mapper = mapper;
    }

    public CareerPlan insert(Long userId, String requestId, String targetRole, String jobDescription, Integer weeks, Integer hoursPerWeek, boolean researchMarket, String resultJson) {
        Long id = mapper.insert(userId, requestId, targetRole, jobDescription, weeks, hoursPerWeek, researchMarket, resultJson);
        return findById(userId, id).orElseThrow(() -> new IllegalStateException("Saved career plan could not be loaded"));
    }

    public Optional<CareerPlan> findByRequestId(Long userId, String requestId) {
        return mapper.findByUserIdAndRequestId(userId, requestId).map(this::toDomain);
    }

    public List<CareerPlan> findByUserId(Long userId) {
        return mapper.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    public Optional<CareerPlan> findById(Long userId, Long planId) {
        return mapper.findByIdAndUserId(planId, userId).map(this::toDomain);
    }

    private CareerPlan toDomain(CareerPlanRow row) {
        return new CareerPlan(row.getId(), row.getUserId(), row.getRequestId(), row.getTargetRole(), row.getJobDescription(), row.getWeeks(), row.getHoursPerWeek(), row.isResearchMarket(), row.getResultJson(), row.getCreatedAt(), row.getUpdatedAt());
    }
}
