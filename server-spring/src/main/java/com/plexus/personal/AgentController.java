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
import java.util.Map;

@RestController
@RequestMapping("/agents/PlexusAgent")
public class AgentController {
    private final ChatService chatService;

    public AgentController(ChatService chatService) {
        this.chatService = chatService;
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
}
