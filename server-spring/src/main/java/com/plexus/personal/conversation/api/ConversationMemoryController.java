package com.plexus.personal.conversation.api;

import com.plexus.personal.auth.infrastructure.security.JwtAuthenticationWebFilter;
import com.plexus.personal.conversation.application.ConversationMemoryService;
import com.plexus.personal.conversation.domain.model.ConversationMemory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/conversations/{conversationId}/memory")
public class ConversationMemoryController {
    private final ConversationMemoryService service;

    public ConversationMemoryController(ConversationMemoryService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<ConversationMemory> read(@PathVariable Long conversationId, ServerWebExchange exchange) {
        Long userId = currentUser(exchange);
        return Mono.fromCallable(() -> service.read(userId, conversationId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> clear(@PathVariable Long conversationId, ServerWebExchange exchange) {
        Long userId = currentUser(exchange);
        return Mono.fromRunnable(() -> service.clear(userId, conversationId))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private Long currentUser(ServerWebExchange exchange) {
        String id = exchange.getAttribute(JwtAuthenticationWebFilter.USER_ID_ATTRIBUTE);
        if (id == null) throw new IllegalStateException("Authenticated user id is missing");
        return Long.valueOf(id);
    }
}
