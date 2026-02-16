# Task 12: Write Tests

> **Dependencies**: All other Phase 2 tasks complete (Tasks 01–11, 10b).

## Summary

Write unit and integration tests for all new Phase 2 components. All `@QuarkusTest` tests use DevServices PostgreSQL. The `pg_trgm` extension is created by the Liquibase migration (Task 01), so it is available automatically in DevServices.

## Changes

### Unit Tests *(created)*

- **`DocChunkBuilderTest.java`** — mock `DocParser`, `DocChunkStore`, `DocStore`; verify section splitting produces correct chunk count, chunk IDs are deterministic, metadata is extracted, `version` is set on chunks, `deleteByVersion` is called before insert
- **`DocChunkSearchServiceTest.java`** — mock `DocChunkStore`; verify fuzzy fallback triggers when FTS returns empty, verify pagination pass-through, verify `ChunkSearchResult` URLs are built

### Integration Tests *(created)*

- **`DocChunkStoreTest.java`** — `@QuarkusTest` (integration, not unit — requires live DB); insert chunks, verify `search()` returns ranked results, verify `fuzzySearch()` returns results for misspelled queries, verify `deleteByVersion()` removes only target version, verify `TEXT[]` arrays round-trip correctly
- **`SearchResourceTest.java`** — `@QuarkusTest` + RestAssured:
  - Insert test chunks via `DocChunkStore` in `@BeforeEach`
  - `GET /api/search?q=reactive` returns 200 with ranked results
  - `GET /api/search?q=reactive&extension=io.quarkus:quarkus-mailer` filters by extension
  - `GET /api/search?q=reative` (typo) returns fuzzy results
  - `GET /api/search?q=reactive&version=3.17` scopes to version
  - `GET /api/search?q=reactive&limit=5&offset=0` returns paginated results
  - `POST /api/search` with `{"q": "reactive"}` body works
  - `GET /api/code-samples` returns 404
  - Verify response JSON matches `ChunkSearchResponse` schema

### Updated Tests *(modified)*

- **`MetaServiceTest.java`** — update assertions for new `/api/search` param names (`q` not `keywords`), verify no `/api/code-samples` in output
- **`SearchSyntaxResourceTest.java`** — update expected syntax description for PostgreSQL FTS

### Out of Scope

- `CacheWarmupJob` / `CacheRefreshJob` test updates — existing tests (`CacheWarmupJobTest`, `CacheRefreshJobTest`, integration tests) need mock updates for `DocChunkBuilder` injection but are mechanical changes best done inline with Task 09
- `UrlBuilderTest` — already created in Task 03

## Acceptance Criteria

- [ ] `./gradlew test` passes with zero failures
- [ ] FTS ranking: higher-ranked chunks appear first
- [ ] Fuzzy fallback: misspelled query returns results
- [ ] Version scoping: only version-matched chunks returned
- [ ] Pagination: `limit` and `offset` produce correct slices
- [ ] POST `/api/search` works with new request body
- [ ] 404: `/api/code-samples` is gone
- [ ] `DocChunkStoreTest` runs as integration test (not unit)
- [ ] `MetaServiceTest` reflects updated search endpoint description

## Files

- `src/test/java/com/fvd/indexs/services/DocChunkBuilderTest.java` *(created)*
- `src/test/java/com/fvd/indexs/stores/DocChunkStoreTest.java` *(created)*
- `src/test/java/com/fvd/search/services/DocChunkSearchServiceTest.java` *(created)*
- `src/test/java/com/fvd/api/resources/SearchResourceTest.java` *(created)*
- `src/test/java/com/fvd/api/services/MetaServiceTest.java` *(modified)*
- `src/test/java/com/fvd/api/resources/SearchSyntaxResourceTest.java` *(modified)*
