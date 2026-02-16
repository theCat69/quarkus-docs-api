# Task 02: Drop Old Tables via Liquibase

> **Dependencies**: Task 01 (doc_chunks table must exist before dropping old tables).

## Summary

Add a Liquibase changeset that drops the 7 legacy indexing tables. Add it to `postgres.yaml` as an explicit `include` entry after `002-create-doc-chunks.sql`. The `github_index` table is **kept** — it is used by `IndexStore` for cache invalidation.

Note: `postgres.yaml` was changed from `includeAll` to explicit includes in Task 01. This task adds the third entry.

## Changes

### `src/main/resources/db/scripts/003-drop-legacy-tables.sql` *(created)*

```sql
-- liquibase formatted sql
-- changeset quarkus-docs-api:003-drop-legacy-tables

DROP TABLE IF EXISTS code_sample_keywords;
DROP TABLE IF EXISTS code_samples;
DROP TABLE IF EXISTS section_keywords;
DROP TABLE IF EXISTS sections;
DROP TABLE IF EXISTS file_keywords;
DROP TABLE IF EXISTS document_metadata;
DROP TABLE IF EXISTS files;
```

Drop order respects foreign key constraints (children before parents).

### `src/main/resources/db/postgres.yaml` *(modified)*

Add the third explicit include entry:

```yaml
  - include:
      file: "scripts/003-drop-legacy-tables.sql"
      relativeToChangelogFile: true
```

## Acceptance Criteria

- [ ] All 7 legacy tables are dropped after migration
- [ ] `github_index` table still exists and is untouched
- [ ] `doc_chunks` table still exists and is untouched
- [ ] `postgres.yaml` lists all three scripts as explicit includes in order
- [ ] Liquibase applies the migration cleanly on startup
- [ ] No orphaned index references remain

## Files

- `src/main/resources/db/scripts/003-drop-legacy-tables.sql` *(created)*
- `src/main/resources/db/postgres.yaml` *(modified)*
