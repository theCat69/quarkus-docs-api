# Task 01: Update doc_chunks DDL & Add Version Column

> **Dependencies**: Phase 1 complete (Liquibase, Agroal datasource, DevServices configured).
> After Phase 1, the init script (`001-init.sql`) contains legacy tables only.

## Summary

Add a Liquibase changeset creating the `doc_chunks` table with `version TEXT NOT NULL`, enable `pg_trgm`, and create all required indexes. Switch `postgres.yaml` from `includeAll` to explicit `include` entries for deterministic ordering.

## Changes

### `src/main/resources/db/postgres.yaml` *(modified)*

Replace `includeAll` with explicit `include` entries:

```yaml
databaseChangeLog:
  - include:
      file: "scripts/init.sql"
      relativeToChangelogFile: true
  - include:
      file: "scripts/002-create-doc-chunks.sql"
      relativeToChangelogFile: true
```

Task 02 will add `003-*` to this list.

### `src/main/resources/db/scripts/002-create-doc-chunks.sql` *(created)*

```sql
-- liquibase formatted sql
-- changeset quarkus-docs-api:002-create-doc-chunks
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE TABLE IF NOT EXISTS doc_chunks (
    id TEXT PRIMARY KEY, version TEXT NOT NULL, page TEXT NOT NULL,
    title TEXT NOT NULL, section TEXT NOT NULL, url TEXT,
    topics TEXT[], extensions TEXT[], summary TEXT,
    content TEXT NOT NULL, content_tsv tsvector
);
CREATE INDEX IF NOT EXISTS idx_doc_chunks_tsv ON doc_chunks USING GIN (content_tsv);
CREATE INDEX IF NOT EXISTS idx_doc_chunks_extensions ON doc_chunks USING GIN (extensions);
CREATE INDEX IF NOT EXISTS idx_doc_chunks_topics ON doc_chunks USING GIN (topics);
CREATE INDEX IF NOT EXISTS idx_doc_chunks_page_trgm ON doc_chunks USING GIN (page gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_doc_chunks_version_page ON doc_chunks (version, page);
```

## Acceptance Criteria

- [ ] `postgres.yaml` uses explicit `include` entries (no `includeAll`)
- [ ] `pg_trgm` extension enabled; `doc_chunks` includes `version TEXT NOT NULL`
- [ ] All indexes use `IF NOT EXISTS`; GIN on `content_tsv`, `extensions`, `topics`, `page` (trigram)
- [ ] Composite index on `(version, page)` exists
- [ ] Liquibase applies cleanly on startup; `./gradlew compileJava` succeeds

## Files

- `src/main/resources/db/scripts/002-create-doc-chunks.sql` *(created)*
- `src/main/resources/db/postgres.yaml` *(modified)*
