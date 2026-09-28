ALTER TABLE documents ADD COLUMN raw_text TEXT;
ALTER TABLE documents ADD COLUMN normalized_text TEXT;
ALTER TABLE documents ADD COLUMN quality_status VARCHAR(30) NOT NULL DEFAULT 'UNREVIEWED';
ALTER TABLE documents ADD COLUMN quality_score DOUBLE PRECISION;
ALTER TABLE documents ADD COLUMN review_result TEXT;
