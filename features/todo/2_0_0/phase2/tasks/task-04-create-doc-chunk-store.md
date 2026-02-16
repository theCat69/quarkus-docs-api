# Task 04: Create DocChunkStore

> **Dependencies**: Task 01 (doc_chunks table must exist).

## Summary

Create `DocChunkStore` in `com.fvd.indexs.stores` — the data-access layer for the `doc_chunks` table. Uses raw JDBC on an injected `DataSource`, matching the existing `IndexStore` pattern (`@RequiredArgsConstructor` + `@ApplicationScoped`). Tests are deferred to Task 12.

## Changes

### `src/main/java/com/fvd/indexs/model/DocChunk.java` *(created)*

New package `com.fvd.indexs.model` (singular, matching `com.fvd.asciidocs.model` convention). Keeps indexing concerns separate from `com.fvd.asciidocs.model`.

Record or Lombok `@Value` class with fields: `id`, `version`, `page`, `title`, `section`, `url`, `topics` (List\<String\>), `extensions` (List\<String\>), `summary`, `content`.

### `src/main/java/com/fvd/indexs/model/ChunkSearchRow.java` *(created)*

Record in the same `com.fvd.indexs.model` package with all `doc_chunks` columns plus `score` (double).

### `src/main/java/com/fvd/indexs/stores/DocChunkStore.java` *(created)*

- `@ApplicationScoped`, `@RequiredArgsConstructor`, injects `DataSource`
- `void insertBatch(String version, List<DocChunk> chunks)` — batch INSERT with `PreparedStatement.addBatch()`. Sets `content_tsv` via `to_tsvector('english', ...)` in SQL. Uses `createArrayOf("text", ...)` for `TEXT[]` columns.
- `void deleteByVersion(String version)` — `DELETE FROM doc_chunks WHERE version = ?`
- `List<ChunkSearchRow> search(String query, String version, String extension, int limit, int offset)` — `WHERE version = ? AND content_tsv @@ plainto_tsquery('english', ?)`, optional `AND extensions @> ARRAY[?]`, `ORDER BY ts_rank(...) DESC`, `LIMIT ? OFFSET ?`
- `List<ChunkSearchRow> fuzzySearch(String query, String version, int limit)` — `ORDER BY similarity(content, ?) DESC` with threshold `> 0.1`, `LIMIT ?`

## Acceptance Criteria

- [ ] `DocChunk` lives in `com.fvd.indexs.model` (not `models`)
- [ ] `ChunkSearchRow` lives in `com.fvd.indexs.model` (not in `stores`)
- [ ] `insertBatch` correctly inserts chunks with `TEXT[]` arrays and computed `tsvector`
- [ ] `deleteByVersion` removes only chunks for the specified version
- [ ] `search` returns results ranked by `ts_rank`, filtered by version and optional extension
- [ ] `fuzzySearch` returns results ranked by `similarity()` when FTS yields nothing
- [ ] All methods use try-with-resources for JDBC connections
- [ ] `./gradlew compileJava` succeeds

## Files

- `src/main/java/com/fvd/indexs/model/DocChunk.java` *(created)*
- `src/main/java/com/fvd/indexs/model/ChunkSearchRow.java` *(created)*
- `src/main/java/com/fvd/indexs/stores/DocChunkStore.java` *(created)*
