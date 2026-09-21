package com.plexus.personal.auth.application;

import com.plexus.personal.auth.api.LoginRequest;
import com.plexus.personal.auth.api.LoginResponse;
import com.plexus.personal.auth.infrastructure.security.JwtTokenService;
import com.plexus.personal.user.domain.model.User;
import com.plexus.personal.user.domain.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.enabled() || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        return jwtTokenService.issue(user);
    }
}
