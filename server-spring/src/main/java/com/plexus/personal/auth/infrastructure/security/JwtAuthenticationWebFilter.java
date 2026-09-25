package com.plexus.personal.auth.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationWebFilter implements WebFilter {

    public static final String USER_ID_ATTRIBUTE = JwtAuthenticationWebFilter.class.getName() + ".userId";
    public static final String USERNAME_ATTRIBUTE = JwtAuthenticationWebFilter.class.getName() + ".username";

    private static final String CURRENT_USER_PATH = "/api/auth/me";
    private static final String AGENT_EXECUTE_PATH = "/agents/PlexusAgent/execute";
    private static final String AGENT_PLAN_PATH = "/agents/PlexusAgent/plan";
    private static final String AGENT_PLANS_PATH = "/agents/PlexusAgent/plans";
    private static final String CONVERSATIONS_PATH = "/api/conversations";
    private static final String RESUME_PATH = "/api/resume";

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationWebFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!requiresAuthentication(exchange)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        String token = authorization.substring("Bearer ".length()).trim();
        if (!jwtTokenService.isValid(token)) {
            return unauthorized(exchange);
        }

        Jws<Claims> claims = jwtTokenService.parse(token);
        exchange.getAttributes().put(USER_ID_ATTRIBUTE, claims.getPayload().getSubject());
        exchange.getAttributes().put(USERNAME_ATTRIBUTE, claims.getPayload().get("username", String.class));
        return chain.filter(exchange);
    }

    private boolean requiresAuthentication(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return CURRENT_USER_PATH.equals(path)
                || (AGENT_EXECUTE_PATH.equals(path)
                && HttpMethod.POST.equals(exchange.getRequest().getMethod()))
                || (AGENT_PLAN_PATH.equals(path)
                && HttpMethod.POST.equals(exchange.getRequest().getMethod()))
                || (path.equals(AGENT_PLANS_PATH)
                && (HttpMethod.GET.equals(exchange.getRequest().getMethod()) || HttpMethod.POST.equals(exchange.getRequest().getMethod())))
                || (path.startsWith(AGENT_PLANS_PATH + "/")
                && HttpMethod.GET.equals(exchange.getRequest().getMethod()))
                || path.equals(CONVERSATIONS_PATH)
                || path.startsWith(CONVERSATIONS_PATH + "/")
                || path.equals(RESUME_PATH);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
