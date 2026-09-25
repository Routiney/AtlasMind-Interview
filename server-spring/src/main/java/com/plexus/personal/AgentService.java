package com.plexus.personal;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class AgentService {
    private final WebClient coreWebClient;
    private final int planTimeoutSeconds;

    public AgentService(
            WebClient coreWebClient,
            @Value("${plexus.core.plan-timeout-seconds:180}") int planTimeoutSeconds
    ) {
        this.coreWebClient = coreWebClient;
        this.planTimeoutSeconds = planTimeoutSeconds;
    }

    public Flux<DataBuffer> execute(CoreChatRequest request) {
        return coreWebClient.post()
                .uri("/agents/PlexusAgent/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .doOnCancel(() -> System.out.println("[server] agent SSE cancelled by client"));
    }

    public Mono<JsonNode> plan(PlanCoreRequest request) {
        return coreWebClient.post()
                .uri("/agents/PlexusAgent/plan")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(planTimeoutSeconds))
                .doOnCancel(() -> System.out.println("[server] planning request cancelled by client"));
    }

    public Mono<JsonNode> memory(MemoryCoreRequest request) {
        return coreWebClient.post()
                .uri("/agents/PlexusAgent/memory")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(60));
    }
}
