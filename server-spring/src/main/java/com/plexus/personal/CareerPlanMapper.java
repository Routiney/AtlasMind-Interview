package com.plexus.personal;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CareerPlanMapper {
    Long insert(
            @Param("userId") Long userId,
            @Param("requestId") String requestId,
            @Param("targetRole") String targetRole,
            @Param("jobDescription") String jobDescription,
            @Param("weeks") Integer weeks,
            @Param("hoursPerWeek") Integer hoursPerWeek,
            @Param("researchMarket") boolean researchMarket,
            @Param("resultJson") String resultJson
    );

    Optional<CareerPlanRow> findByUserIdAndRequestId(
            @Param("userId") Long userId,
            @Param("requestId") String requestId
    );

    List<CareerPlanRow> findByUserId(@Param("userId") Long userId);

    Optional<CareerPlanRow> findByIdAndUserId(@Param("planId") Long planId, @Param("userId") Long userId);
}
