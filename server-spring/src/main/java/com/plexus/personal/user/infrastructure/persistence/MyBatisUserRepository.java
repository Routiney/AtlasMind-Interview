package com.plexus.personal.user.infrastructure.persistence;

import com.plexus.personal.user.domain.model.User;
import com.plexus.personal.user.domain.repository.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisUserRepository implements UserRepository {

    private final UserMapper userMapper;

    public MyBatisUserRepository(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userMapper.findByUsername(username).map(this::toDomain);
    }

    @Override
    public User save(String username, String passwordHash, String displayName) {
        int insertedRows = userMapper.insert(username, passwordHash, displayName);
        if (insertedRows != 1) {
            throw new IllegalStateException("Expected one inserted user but got " + insertedRows);
        }
        return findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Created user could not be loaded"));
    }

    private User toDomain(UserRow row) {
        return new User(
                row.getId(),
                row.getUsername(),
                row.getPasswordHash(),
                row.getDisplayName(),
                row.isEnabled(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }
}
