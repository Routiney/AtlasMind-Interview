package com.plexus.personal;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Service
public class AgentService {
    private final WebClient coreWebClient;

    public AgentService(WebClient coreWebClient) {
        this.coreWebClient = coreWebClient;
    }

    public Flux<DataBuffer> execute(ChatRequest request) {
        return coreWebClient.post()
                .uri("/agents/PlexusAgent/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .doOnCancel(() -> System.out.println("[server] agent SSE cancelled by client"));
    }
}
