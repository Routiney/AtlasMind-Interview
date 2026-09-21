CREATE TABLE resume_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    target_role VARCHAR(100) NOT NULL,
    summary VARCHAR(2000) NOT NULL DEFAULT '',
    education VARCHAR(4000) NOT NULL DEFAULT '',
    internship VARCHAR(4000) NOT NULL DEFAULT '',
    projects VARCHAR(6000) NOT NULL DEFAULT '',
    skills VARCHAR(2000) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_resume_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_resume_profiles_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
