# Phase 2: doc_chunks + tsvector Re-architecture

## Goal

Replace the custom keyword/code-sample indexing system (SQLite-backed, Java-side scoring, in-memory caching) with PostgreSQL full-text search using a unified `doc_chunks` table, section-level chunking, and DB-side ranking via `ts_rank` / `plainto_tsquery`.

## Scope

### In scope
- New `doc_chunks` table with `version` column, `TEXT[]` arrays, `tsvector` column, and GIN indexes
- `DocChunkBuilder` service: AsciiDoc → section chunks → insert with weighted tsvector
- `DocChunkStore`: CRUD + search queries (tsquery, ts_rank, pg_trgm fallback)
- `DocChunkSearchService`: thin orchestrator returning ranked results
- Unified search endpoint (`/api/search`) replacing file, section, and code-sample search
- URL builder utility (version + page → full quarkus.io URL)
- Updated DTOs with `url`, `topics`, `extensions`, `score` fields
- Liquibase changeset: create `doc_chunks`, drop old SQLite tables
- Update `CacheWarmupJob` / `CacheRefreshJob` to call `DocChunkBuilder`
- Enable `pg_trgm` extension for fuzzy fallback via `similarity()`

### Out of scope
- `DocStore` (filesystem) — unchanged
- `DocParser` interface — unchanged (still used by `DocChunkBuilder`)
- `CacheService` (filesystem) — unchanged
- `github_index` table / `IndexStore` — unchanged (cache invalidation)
- Quarkiverse pipeline — unchanged (feeds file paths into new builder)
- API authentication / rate limiting

## Tasks

1. **Add `version` column to `doc_chunks` DDL** — update `init.sql`, add composite index on `(version, page)`. Add Liquibase changeset under `src/main/resources/db/scripts/`.
2. **Drop old tables via Liquibase** — `files`, `file_keywords`, `sections`, `section_keywords`, `code_samples`, `code_sample_keywords`, `document_metadata`.
3. **Create `DocChunkBuilder`** — new `com.fvd.indexs.services.DocChunkBuilder`. Uses `DocParser.parseSections()` to split each .adoc into section chunks. Extracts title, section heading, topics/extensions from doc header, generates summary (first sentence of section), builds `id` as `page-slug + section-slug`. Computes weighted tsvector (A: title+section, B: summary, C: content). Batch-inserts into `doc_chunks` via `DocChunkStore`. Files: new `DocChunkBuilder.java`.
4. **Create `DocChunkStore`** — new `com.fvd.indexs.stores.DocChunkStore`. Methods: `insertBatch(version, List<DocChunk>)`, `deleteByVersion(version)`, `search(query, version, extension, limit, offset)` using tsquery+ts_rank, `fuzzySearch(query, version, limit)` using `similarity()`. Files: new `DocChunkStore.java`.
5. **Create `UrlBuilder` utility** — new `com.fvd.common.utils.UrlBuilder`. Builds `https://quarkus.io/guides/{page}#{section-anchor}` from version + page + section. Files: new `UrlBuilder.java`.
6. **Create `DocChunkSearchService`** — new `com.fvd.search.services.DocChunkSearchService`. Delegates to `DocChunkStore.search()`, falls back to `DocChunkStore.fuzzySearch()` when FTS returns zero results. Returns `PaginatedResult<ChunkSearchResult>`. Files: new `DocChunkSearchService.java`, new `ChunkSearchResult.java`.
7. **Create unified search DTOs** — new `ChunkSearchResponse` with `results[]` containing `id`, `page`, `title`, `section`, `summary`, `extensions`, `topics`, `score`, `url`. Files: new `com.fvd.api.dto.ChunkSearchResponse.java`.
8. **Replace search endpoint** — update `SearchResource` to delegate to `DocChunkSearchService`. Remove `/api/code-samples` endpoint (`CodeSampleResource`). Merge section search into unified endpoint. Files: modified `SearchResource.java`, deleted `CodeSampleResource.java`.
9. **Update cache jobs** — replace `KeywordIndexer` / `CodeSampleIndexer` calls with `DocChunkBuilder.build()` in `CacheWarmupJob` and `CacheRefreshJob`. Files: modified `CacheWarmupJob.java`, `CacheRefreshJob.java`.
10. **Delete dead code** — remove `KeywordIndexer`, `CodeSampleIndexer`, `KeywordIndexStore`, `CodeSampleIndexStore`, `DocumentMetadataStore`, `SqliteSearchScorer`, `SearchScorer`, `SearchKeywords`, `KeywordScorer`, `Stemmer`, `StopWords`, `KeywordIndex`, `CodeSampleIndex`, in-memory `indexCache` in `SearchService`, `QuickSearchService`, `CodeSampleService`. Files: delete ~15 files under `com.fvd.indexs.*`, `com.fvd.search.services.*`, `com.fvd.common.*`.
11. **Update `MetaResource` / `MetaService`** — reflect new unified endpoint and removed endpoints in capabilities response. Files: modified `MetaService.java`, `MetaResource.java`.
12. **Write tests** — unit tests for `DocChunkBuilder` (section splitting, tsvector generation), `DocChunkStore` (search ranking, fuzzy fallback), `UrlBuilder`. Integration tests for unified search endpoint (keyword match, extension boost, fuzzy typo, pagination, version scoping). Files: new test classes under `src/test/java/`.

## Acceptance Criteria

1. `GET /api/search?q=reactive+mailer&version=main` returns ranked chunks from `doc_chunks` with `score`, `url`, `section`, `extensions` fields
2. Extension boost: passing `extension=io.quarkus:quarkus-mailer` raises relevant chunks in ranking
3. Fuzzy fallback: misspelled query (e.g., `reative`) still returns results via `pg_trgm` similarity
4. Version scoping: `?version=3.17` only returns chunks indexed for that version
5. Old endpoints (`/api/code-samples`) return 404
6. `KeywordIndexer`, `CodeSampleIndexer`, `SqliteSearchScorer`, in-memory `indexCache` are fully removed
7. Cache warmup completes successfully using `DocChunkBuilder`
8. `./gradlew test` passes with zero failures

## Dependencies

- **Phase 1 (PostgreSQL engine swap)** must be complete — Liquibase, Agroal datasource, DevServices configured
- `pg_trgm` extension available in PostgreSQL (standard contrib module, enabled via `CREATE EXTENSION`)
- `DocParser.parseSections()` must reliably split on `==` headings (already implemented in `AsciidocParser`)

## Risks

- **Section granularity may be too coarse** for long guides (50+ sections). Monitor chunk sizes; may need sub-section splitting (`===`) in a follow-up.
- **tsvector `english` config** doesn't handle Quarkus-specific terms (e.g., "CDI", "RESTEasy"). These pass through unstemmed, which is fine for exact match but won't match inflections. Acceptable trade-off.
- **Summary generation** (first sentence heuristic) may produce low-quality summaries for sections that start with code blocks. Consider falling back to section title when content starts with `[source`.
- **Migration data loss** — dropping old tables is irreversible. Ensure cache warmup rebuilds all versions before cutting over. Run both systems in parallel during dev/test.
- **`/api/code-samples` removal is breaking** — document in changelog. Consumers must migrate to unified `/api/search`.
- **pg_trgm similarity on large `content` columns** can be slow without a trigram GIN index on `content`. Add `CREATE INDEX idx_doc_chunks_content_trgm ON doc_chunks USING GIN (content gin_trgm_ops)` if fuzzy fallback is too slow.
