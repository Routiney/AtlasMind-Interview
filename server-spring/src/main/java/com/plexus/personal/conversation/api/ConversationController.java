package com.plexus.personal.conversation.api;

import com.plexus.personal.auth.infrastructure.security.JwtAuthenticationWebFilter;
import com.plexus.personal.conversation.application.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse create(
            @Valid @RequestBody CreateConversationRequest request,
            ServerWebExchange exchange
    ) {
        return ConversationResponse.from(
                conversationService.create(currentUserId(exchange), request.title())
        );
    }

    @GetMapping
    public ConversationPageResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            ServerWebExchange exchange
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        return ConversationPageResponse.from(
                conversationService.findForUser(currentUserId(exchange), safeSize + 1, safePage * safeSize),
                safePage,
                safeSize
        );
    }

    @GetMapping("/{conversationId}/messages")
    public MessagePageResponse messages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            ServerWebExchange exchange
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return MessagePageResponse.from(
                conversationService.findMessages(currentUserId(exchange), conversationId, safeSize + 1, safePage * safeSize),
                safePage,
                safeSize
        );
    }

    @PatchMapping("/{conversationId}")
    public ConversationResponse updateTitle(
            @PathVariable Long conversationId,
            @Valid @RequestBody UpdateConversationRequest request,
            ServerWebExchange exchange
    ) {
        return ConversationResponse.from(
                conversationService.updateTitle(currentUserId(exchange), conversationId, request.title())
        );
    }

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long conversationId,
            ServerWebExchange exchange
    ) {
        conversationService.delete(currentUserId(exchange), conversationId);
    }

    private Long currentUserId(ServerWebExchange exchange) {
        String userId = exchange.getAttribute(JwtAuthenticationWebFilter.USER_ID_ATTRIBUTE);
        if (userId == null) {
            throw new IllegalStateException("Authenticated user id is missing");
        }
        return Long.valueOf(userId);
    }
}
