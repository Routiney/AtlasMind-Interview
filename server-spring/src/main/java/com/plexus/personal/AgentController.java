package com.plexus.personal;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import jakarta.validation.Valid;
import com.plexus.personal.auth.infrastructure.security.JwtAuthenticationWebFilter;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Map;

@RestController
@RequestMapping("/agents/PlexusAgent")
public class AgentController {
    private final ChatService chatService;
    private final PlanningService planningService;

    public AgentController(ChatService chatService, PlanningService planningService) {
        this.chatService = chatService;
        this.planningService = planningService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "server-spring");
    }

    @PostMapping(value = "/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<DataBuffer> execute(
            @Valid @RequestBody ChatRequest request,
            ServerWebExchange exchange
    ) {
        String userId = exchange.getAttribute(JwtAuthenticationWebFilter.USER_ID_ATTRIBUTE);
        if (userId == null) {
            throw new IllegalStateException("Authenticated user id is missing");
        }
        return chatService.execute(Long.valueOf(userId), request);
    }

    @PostMapping(value = "/plan", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<JsonNode> plan(
            @Valid @RequestBody PlanRequest request,
            ServerWebExchange exchange
    ) {
        String userId = exchange.getAttribute(JwtAuthenticationWebFilter.USER_ID_ATTRIBUTE);
        if (userId == null) {
            throw new IllegalStateException("Authenticated user id is missing");
        }
        return planningService.generateAndSave(Long.valueOf(userId), request);
    }

    @GetMapping("/plans")
    public java.util.List<CareerPlanSummaryResponse> plans(ServerWebExchange exchange) {
        return planningService.findAll(currentUserId(exchange));
    }

    @GetMapping("/plans/{planId}")
    public JsonNode plan(@PathVariable Long planId, ServerWebExchange exchange) {
        return planningService.findOne(currentUserId(exchange), planId);
    }

    private Long currentUserId(ServerWebExchange exchange) {
        String userId = exchange.getAttribute(JwtAuthenticationWebFilter.USER_ID_ATTRIBUTE);
        if (userId == null) {
            throw new IllegalStateException("Authenticated user id is missing");
        }
        return Long.valueOf(userId);
    }
}
