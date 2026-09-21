package com.plexus.personal.auth.api;

import com.plexus.personal.auth.application.AuthService;
import com.plexus.personal.auth.infrastructure.security.JwtAuthenticationWebFilter;
import com.plexus.personal.user.application.RegisterUserRequest;
import com.plexus.personal.user.application.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final boolean devLoginEnabled;
    private final String devLoginPassword;

    public AuthController(
            AuthService authService,
            UserService userService,
            @Value("${atlasmind.dev-login:false}") boolean devLoginEnabled,
            @Value("${atlasmind.dev-login-password}") String devLoginPassword
    ) {
        this.authService = authService;
        this.userService = userService;
        this.devLoginEnabled = devLoginEnabled;
        this.devLoginPassword = devLoginPassword;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/dev-login")
    public LoginResponse devLogin() {
        if (!devLoginEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        String username = "dev-user";
        userService.findByUsername(username).orElseGet(() -> userService.register(
                new RegisterUserRequest(username, devLoginPassword, "开发测试用户")
        ));
        return authService.login(new LoginRequest(username, devLoginPassword));
    }

    @GetMapping("/me")
    public Map<String, String> me(ServerWebExchange exchange) {
        return Map.of(
                "userId", (String) exchange.getAttribute(JwtAuthenticationWebFilter.USER_ID_ATTRIBUTE),
                "username", (String) exchange.getAttribute(JwtAuthenticationWebFilter.USERNAME_ATTRIBUTE)
        );
    }
}
