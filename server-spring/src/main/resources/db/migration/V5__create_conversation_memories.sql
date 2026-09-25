CREATE TABLE conversation_memories (
    conversation_id BIGINT PRIMARY KEY,
    summary TEXT NOT NULL DEFAULT '',
    facts_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    version BIGINT NOT NULL DEFAULT 0,
    forgotten_before BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_conversation_memories_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE
);
