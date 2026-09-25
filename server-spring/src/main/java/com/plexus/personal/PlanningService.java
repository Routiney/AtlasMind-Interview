package com.plexus.personal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.plexus.personal.resume.application.ResumeProfileService;
import com.plexus.personal.resume.domain.model.ResumeProfile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PlanningService {
    private static final Set<String> INTERNAL_RESULT_FIELDS = Set.of(
            "resume_evidence",
            "evidence_context",
            "experience_supported",
            "self_declared_only",
            "task_type",
            "query"
    );
    private final ResumeProfileService resumeProfileService;
    private final AgentService agentService;
    private final CareerPlanRepository careerPlanRepository;
    private final ObjectMapper objectMapper;

    public PlanningService(ResumeProfileService resumeProfileService, AgentService agentService, CareerPlanRepository careerPlanRepository, ObjectMapper objectMapper) {
        this.resumeProfileService = resumeProfileService;
        this.agentService = agentService;
        this.careerPlanRepository = careerPlanRepository;
        this.objectMapper = objectMapper;
    }

    public Mono<JsonNode> execute(Long userId, PlanRequest request) {
        return resumeProfileService.findByUserId(userId)
                .map(this::toResumeContext)
                .map(profile -> new PlanCoreRequest(
                        profile,
                        request.jobDescription() == null ? "" : request.jobDescription().trim(),
                        request.weeks() == null ? 4 : request.weeks(),
                        request.hoursPerWeek() == null ? 8 : request.hoursPerWeek(),
                        request.researchMarket()
                ))
                .map(agentService::plan)
                .orElseGet(() -> Mono.error(new ResumeRequiredException()));
    }

    public Mono<JsonNode> generateAndSave(Long userId, PlanRequest request) {
        String requestId = requestId(request);
        CareerPlan existing = careerPlanRepository.findByRequestId(userId, requestId).orElse(null);
        if (existing != null) {
            return Mono.just(readResult(existing));
        }
        return execute(userId, request)
                .map(result -> {
                    CareerPlan saved;
                    try {
                        saved = careerPlanRepository.insert(
                                userId,
                                requestId,
                                targetRole(result),
                                request.jobDescription() == null ? "" : request.jobDescription().trim(),
                                request.weeks() == null ? 4 : request.weeks(),
                                request.hoursPerWeek() == null ? 8 : request.hoursPerWeek(),
                                request.researchMarket(),
                                result.toString()
                        );
                    } catch (DuplicateKeyException duplicate) {
                        saved = careerPlanRepository.findByRequestId(userId, requestId)
                                .orElseThrow(() -> duplicate);
                    }
                    return withPlanId(result, saved.id());
                });
    }

    public java.util.List<CareerPlanSummaryResponse> findAll(Long userId) {
        return careerPlanRepository.findByUserId(userId).stream().map(CareerPlanSummaryResponse::from).toList();
    }

    public JsonNode findOne(Long userId, Long planId) {
        CareerPlan plan = careerPlanRepository.findById(userId, planId).orElseThrow(CareerPlanNotFoundException::new);
        return readResult(plan);
    }

    private JsonNode readResult(CareerPlan plan) {
        try {
            JsonNode result = sanitizePublicResult(objectMapper.readTree(plan.resultJson()));
            return withPlanId(result, plan.id());
        } catch (Exception exception) {
            throw new IllegalStateException("职业规划结果无法解析", exception);
        }
    }

    private JsonNode sanitizePublicResult(JsonNode value) {
        if (value == null || value.isNull()) {
            return value;
        }
        if (value.isObject()) {
            ObjectNode object = value.deepCopy();
            Iterator<String> fields = object.fieldNames();
            Set<String> toRemove = new HashSet<>();
            while (fields.hasNext()) {
                String field = fields.next();
                if (INTERNAL_RESULT_FIELDS.contains(field)) {
                    toRemove.add(field);
                } else {
                    object.set(field, sanitizePublicResult(object.get(field)));
                }
            }
            object.remove(toRemove);
            return object;
        }
        if (value.isArray()) {
            ArrayNode array = value.deepCopy();
            for (int index = 0; index < array.size(); index++) {
                array.set(index, sanitizePublicResult(array.get(index)));
            }
            return array;
        }
        if (value.isTextual()) {
            return objectMapper.getNodeFactory().textNode(
                    value.asText()
                            .replace("experience_supported", "有经历支撑")
                            .replace("self_declared_only", "仅在技能栏提及")
                            .replace("evidence_strength", "证据来源")
                            .replace("resume_evidence", "简历证据")
                            .replace("evidence_context", "参考依据")
                            .replace("job_match", "岗位匹配")
                            .replace("job_directions", "岗位方向")
                            .replace("task_type", "任务类型")
                            .replace("query", "检索内容")
            );
        }
        return value;
    }

    private JsonNode withPlanId(JsonNode result, Long planId) {
        if (result instanceof ObjectNode objectNode) {
            objectNode.put("plan_id", planId);
        }
        return result;
    }

    private String requestId(PlanRequest request) {
        return request.requestId() == null || request.requestId().isBlank()
                ? UUID.randomUUID().toString()
                : request.requestId().trim();
    }

    private String targetRole(JsonNode result) {
        JsonNode targetRole = result.path("learning_plan").path("target_role");
        if (!targetRole.isTextual() || targetRole.asText().isBlank()) {
            return "未命名岗位方向";
        }
        String value = targetRole.asText().trim();
        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    private Map<String, String> toResumeContext(ResumeProfile profile) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("name", profile.name());
        context.put("target_role", profile.targetRole());
        context.put("summary", profile.summary());
        context.put("education", profile.education());
        context.put("internship", profile.internship());
        context.put("projects", profile.projects());
        context.put("skills", profile.skills());
        return context;
    }
}
