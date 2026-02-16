-- liquibase formatted sql
-- changeset quarkus-docs-api:001-init-schema

CREATE TABLE IF NOT EXISTS files (
    id BIGSERIAL PRIMARY KEY,
    version TEXT NOT NULL,
    path TEXT NOT NULL,
    extension TEXT NOT NULL DEFAULT 'quarkus-core',
    UNIQUE(version, path)
);

CREATE TABLE IF NOT EXISTS file_keywords (
    id BIGSERIAL PRIMARY KEY,
    file_id BIGINT NOT NULL,
    word TEXT NOT NULL,
    original_word TEXT,
    score INTEGER NOT NULL,
    source TEXT NOT NULL DEFAULT 'body',
    frequency INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS sections (
    id BIGSERIAL PRIMARY KEY,
    file_id BIGINT NOT NULL,
    title TEXT NOT NULL,
    start_line INTEGER NOT NULL,
    end_line INTEGER NOT NULL,
    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS section_keywords (
    id BIGSERIAL PRIMARY KEY,
    section_id BIGINT NOT NULL,
    word TEXT NOT NULL,
    original_word TEXT,
    score INTEGER NOT NULL,
    source TEXT NOT NULL DEFAULT 'body',
    frequency INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (section_id) REFERENCES sections(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS github_index (
    id BIGSERIAL PRIMARY KEY,
    version TEXT NOT NULL,
    name TEXT NOT NULL,
    path TEXT NOT NULL,
    sha TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS code_samples (
    id BIGSERIAL PRIMARY KEY,
    version TEXT NOT NULL,
    file_path TEXT NOT NULL,
    section_title TEXT NOT NULL,
    language TEXT NOT NULL,
    content TEXT NOT NULL,
    start_line INTEGER NOT NULL,
    end_line INTEGER NOT NULL,
    extension TEXT NOT NULL DEFAULT 'quarkus-core'
);

CREATE TABLE IF NOT EXISTS code_sample_keywords (
    id BIGSERIAL PRIMARY KEY,
    sample_id BIGINT NOT NULL,
    word TEXT NOT NULL,
    score INTEGER NOT NULL,
    FOREIGN KEY (sample_id) REFERENCES code_samples(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS document_metadata (
    id BIGSERIAL PRIMARY KEY,
    file_id BIGINT NOT NULL UNIQUE,
    categories TEXT,
    topics TEXT,
    extensions_gav TEXT,
    summary TEXT,
    diataxis_type TEXT,
    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_files_version ON files(version);
CREATE INDEX IF NOT EXISTS idx_file_keywords_file_id ON file_keywords(file_id);
CREATE INDEX IF NOT EXISTS idx_file_keywords_word ON file_keywords(word);
CREATE INDEX IF NOT EXISTS idx_sections_file_id ON sections(file_id);
CREATE INDEX IF NOT EXISTS idx_section_keywords_section_id ON section_keywords(section_id);
CREATE INDEX IF NOT EXISTS idx_section_keywords_word ON section_keywords(word);
CREATE INDEX IF NOT EXISTS idx_github_index_version ON github_index(version);
CREATE INDEX IF NOT EXISTS idx_code_samples_version ON code_samples(version);
CREATE INDEX IF NOT EXISTS idx_code_sample_keywords_sample_id ON code_sample_keywords(sample_id);
CREATE INDEX IF NOT EXISTS idx_code_sample_keywords_word ON code_sample_keywords(word);
CREATE INDEX IF NOT EXISTS idx_document_metadata_file_id ON document_metadata(file_id);
