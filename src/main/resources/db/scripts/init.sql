CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE doc_chunks (
  id TEXT PRIMARY KEY,
  page TEXT NOT NULL,           -- asciidoc file name
  title TEXT NOT NULL,          -- doc title
  section TEXT NOT NULL,        -- section / subsection
  url TEXT,                     -- public doc URL
  topics TEXT[],                -- :topics:
  extensions TEXT[],            -- :extensions:
  summary TEXT,                 -- short LLM-generated or heuristic summary
  content TEXT NOT NULL,        -- chunk content (code + explanation)
  content_tsv tsvector          -- full-text index
);

CREATE INDEX idx_doc_chunks_tsv ON doc_chunks USING GIN (content_tsv);
CREATE INDEX idx_doc_chunks_extensions ON doc_chunks USING GIN (extensions);
CREATE INDEX idx_doc_chunks_topics ON doc_chunks USING GIN (topics);
CREATE INDEX idx_doc_chunks_page_trgm ON doc_chunks USING GIN (page gin_trgm_ops);