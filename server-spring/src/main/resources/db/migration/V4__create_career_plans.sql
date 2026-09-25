CREATE TABLE career_plans (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    target_role VARCHAR(200) NOT NULL,
    job_description TEXT NOT NULL DEFAULT '',
    weeks INTEGER NOT NULL,
    hours_per_week INTEGER NOT NULL,
    research_market BOOLEAN NOT NULL DEFAULT FALSE,
    result_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_career_plans_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_career_plans_user_updated
    ON career_plans (user_id, updated_at DESC, id DESC);
