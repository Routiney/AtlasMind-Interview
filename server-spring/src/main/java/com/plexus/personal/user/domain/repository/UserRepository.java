package com.plexus.personal.user.domain.repository;

import com.plexus.personal.user.domain.model.User;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findByUsername(String username);

    User save(String username, String passwordHash, String displayName);
}
