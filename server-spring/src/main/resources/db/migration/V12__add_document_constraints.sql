-- V8 was already applied in existing local databases; constraints belong in a new migration.
UPDATE documents SET status = 'PENDING' WHERE status = 'UPLOADED';

ALTER TABLE documents
    ADD CONSTRAINT uk_documents_storage_key UNIQUE (storage_key),
    ADD CONSTRAINT ck_documents_file_size_positive CHECK (file_size > 0),
    ADD CONSTRAINT ck_documents_status CHECK (status IN ('PENDING', 'READY', 'NEEDS_REVIEW', 'FAILED'));
