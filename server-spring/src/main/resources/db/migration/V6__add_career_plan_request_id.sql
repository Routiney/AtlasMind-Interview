ALTER TABLE career_plans
    ADD COLUMN request_id VARCHAR(64);

UPDATE career_plans
SET request_id = 'legacy-' || id
WHERE request_id IS NULL;

ALTER TABLE career_plans
    ALTER COLUMN request_id SET NOT NULL;

CREATE UNIQUE INDEX uk_career_plans_user_request
    ON career_plans (user_id, request_id);
