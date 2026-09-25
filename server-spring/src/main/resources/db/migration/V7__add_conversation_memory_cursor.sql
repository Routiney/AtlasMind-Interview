ALTER TABLE conversation_memories
    ADD COLUMN covered_until_message_id BIGINT NOT NULL DEFAULT 0;
