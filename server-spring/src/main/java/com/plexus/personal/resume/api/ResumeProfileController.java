package com.plexus.personal.resume.api;

import com.plexus.personal.auth.infrastructure.security.JwtAuthenticationWebFilter;
import com.plexus.personal.resume.application.ResumeProfileRequest;
import com.plexus.personal.resume.application.ResumeProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

@RestController
@RequestMapping("/api/resume")
public class ResumeProfileController {
    private final ResumeProfileService service;

    public ResumeProfileController(ResumeProfileService service) { this.service = service; }

    @GetMapping
    public ResumeProfileResponse get(ServerWebExchange exchange) {
        return service.findByUserId(currentUserId(exchange)).map(ResumeProfileResponse::from).orElse(null);
    }

    @PutMapping
    public ResumeProfileResponse save(@Valid @RequestBody ResumeProfileRequest request, ServerWebExchange exchange) {
        return ResumeProfileResponse.from(service.save(currentUserId(exchange), request));
    }

    private Long currentUserId(ServerWebExchange exchange) {
        String userId = exchange.getAttribute(JwtAuthenticationWebFilter.USER_ID_ATTRIBUTE);
        if (userId == null) throw new IllegalStateException("Authenticated user id is missing");
        return Long.valueOf(userId);
    }
}
