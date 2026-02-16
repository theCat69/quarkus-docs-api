-- liquibase formatted sql
-- changeset quarkus-docs-api:002-create-doc-chunks

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS doc_chunks (
    id TEXT PRIMARY KEY,
    version TEXT NOT NULL,
    page TEXT NOT NULL,
    title TEXT NOT NULL,
    section TEXT NOT NULL,
    url TEXT,
    topics TEXT[],
    extensions TEXT[],
    summary TEXT,
    content TEXT NOT NULL,
    content_tsv tsvector
);

CREATE INDEX IF NOT EXISTS idx_doc_chunks_tsv ON doc_chunks USING GIN (content_tsv);
CREATE INDEX IF NOT EXISTS idx_doc_chunks_extensions ON doc_chunks USING GIN (extensions);
CREATE INDEX IF NOT EXISTS idx_doc_chunks_topics ON doc_chunks USING GIN (topics);
CREATE INDEX IF NOT EXISTS idx_doc_chunks_page_trgm ON doc_chunks USING GIN (page gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_doc_chunks_version_page ON doc_chunks (version, page);
