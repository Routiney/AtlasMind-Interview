package com.plexus.personal.user.application;

import com.plexus.personal.user.domain.model.User;
import com.plexus.personal.user.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User requireByUsername(String username) {
        return findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
    }

    public User register(RegisterUserRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new UserAlreadyExistsException(request.username());
        }

        String passwordHash = passwordEncoder.encode(request.password());
        return userRepository.save(request.username(), passwordHash, request.displayName());
    }
}
