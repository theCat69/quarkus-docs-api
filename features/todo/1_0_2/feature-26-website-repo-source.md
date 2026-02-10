# Feature 26: Switch Doc Source to Website Repository

> **Dependencies**: None. This is a foundational change. Feature 27 (Quarkiverse Documentation Ingestion) depends on this feature being completed first.

Switch the documentation source from `quarkusio/quarkus` (one zip per version) to `quarkusio/quarkusio.github.io` (one zip for all versions). The website repository's `main` branch contains all versions under `_versions/<version>/guides/*.adoc`. This eliminates per-version zip downloads and centralizes all documentation in a single fetch.

> **Note**: This feature also adds an `extension` field to all DTOs and SQLite tables to distinguish core docs from quarkiverse docs (Feature 27). Core docs use `"quarkus-core"` as the extension value. This is a forward-looking addition that Feature 27 will leverage.

## Scope and behavior

- Change `app.github.repo` from `quarkus` to `quarkusio.github.io` in `application.properties`.
- Add a new config key `app.github.branch` with default value `main` — this is the branch to download the zip from. The zip URL becomes `quarkusio/quarkusio.github.io/archive/refs/heads/main.zip`.
- Inside the zip, docs are at `_versions/<version>/guides/<file>.adoc` where `<version>` matches the subdirectory name (e.g., `3.21`, `3.27`, `main`).
- `app.versions` config is **kept** as a filter. Only versions that appear in both the zip AND the config list are cached. If a version is in the config but not in the zip, it is skipped with a warning log.
- **`DocParser.docsPrefix(String version)`** is the PRIMARY new abstract method on the interface, returning `_versions/<version>/guides/`. The zero-arg `docsPrefix()` becomes a `default` method on the `DocParser` interface that delegates to `docsPrefix("main")`. In `AsciidocParser`, the zero-arg override is marked `@Deprecated` and also delegates to `docsPrefix("main")`.
- **`ZipDownloadService.streamAndExtract(String version)`** is replaced with a two-phase approach:
  - `streamAndExtractAll(List<String> versions)` — downloads the zip ONCE, iterates all entries, and for each entry matching `_versions/<V>/guides/*.adoc` where `<V>` is in the versions list, extracts it to `.cache/<V>/docs/<file>.adoc`. Returns a `Map<String, List<String>>` mapping version to list of extracted file paths.
  - The old `streamAndExtract(String version)` method is kept but delegates to `streamAndExtractAll(List.of(version))` and returns the list for that single version.
- **`GitHubService.fetchZipStream(String version)`** is replaced with `fetchZipStream()` (no version param) — it always fetches the `app.github.branch` zip.
- **`GithubRepositoryClient.fetchZipStream(owner, repo, version)`** — no change to the REST client interface; the caller (`GitHubService`) passes `app.github.branch` instead of the doc version.
- **`CacheWarmupJob.onStartup()`** — downloads the zip ONCE via `zipDownloadService.streamAndExtractAll(versions)`, then iterates the result map to build indexes per version.
- **`CacheRefreshJob`** — KEEPS the SHA-comparison approach but adapts it to the new repo structure. `GitHubService.fetchIndex(version)` now calls the GitHub Contents API with the path `_versions/<version>/guides` and ref `main` (the website repo branch). The SHA comparison, per-file re-fetch, and index rebuild logic remain the same — only the path and ref parameters change.
- **`CacheRefreshJob.stripDocsPrefix(String path)`** — updated to use `docParser.docsPrefix(version)` with the version extracted from the path context.
- **`GitHubService.fetchIndex(String version)`** — updated to call `githubApiClient.fetchIndex(owner, repo, "_versions/" + version + "/guides", branch)`. The `ref` parameter uses `app.github.branch` since the website repo organizes versions as directories, not branches.
- **`SearchService.buildIndex(String version)`** — the lazy-download path (lines 487-502 in current code) that calls `zipDownloadService.streamAndExtract(version)` on-the-fly is **removed**. After this change, `getOrBuildIndex()` only loads from the SQLite store; if the index doesn't exist, it returns null. Indexes are always built by the warmup/refresh jobs.
- Cache layout remains: `.cache/<version>/docs/<file>.adoc` — unchanged from the consumer's perspective.
- The file suffix remains `.adoc`. `DocParser.fileSuffix()` is unchanged.
- **Extension field**: Add `public String extension` field to all result DTOs (`FileSearchResult`, `SectionSearchResult`, `CodeSampleSearchResult`, `ContentSearchResult`, `SectionContentResult`, `DocResponse`). Add an `extension` column to all relevant SQLite tables (`keyword_files`, `keyword_sections`, `code_samples`, `content_words`). Indexers receive the extension name (default `"quarkus-core"`) and store it alongside each entry. Add an optional `extension` query parameter filter on all search and doc endpoints.
- **`IndexService.getOrFetchIndex(version)`** — still works because `GitHubService.fetchIndex()` now queries `_versions/<version>/guides` with `ref=main`. No signature changes.

## Internal interfaces

- **`DocParser`** (interface) — add `String docsPrefix(String version)` as a new abstract method. Change existing `String docsPrefix()` to a `default` method: `default String docsPrefix() { return docsPrefix("main"); }`.
  - `docsPrefix(String version)` returns `"_versions/" + version + "/guides/"`.
- **`AsciidocParser`** — implement `docsPrefix(String version)` returning `"_versions/" + version + "/guides/"`. Override `docsPrefix()` with `@Deprecated`, delegating to `docsPrefix("main")`. Remove the `DOCS_PREFIX` static constant.
- **`ZipDownloadService`** — add `Map<String, List<String>> streamAndExtractAll(List<String> versions)`. Refactor `extractToStaging()` to accept a list of versions and match entries against `_versions/<V>/guides/*.adoc` for each V. Refactor `extractRelativePath(String entryName)` to `extractRelativePath(String entryName, String version)` using `docParser.docsPrefix(version)`. Refactor `streamAndExtract(String version)` to delegate to `streamAndExtractAll`.
- **`GitHubService`** — add `@ConfigProperty(name = "app.github.branch", defaultValue = "main") String branch`. Change `fetchZipStream(String version)` to `fetchZipStream()` (uses `branch`). Update `fetchIndex(String version)` to use version-specific docs path and branch as ref.
- **`CacheWarmupJob`** — refactor `onStartup()` to call `streamAndExtractAll(versions)` once, then loop the result map to build indexes per version.
- **`CacheRefreshJob`** — update `stripDocsPrefix(String path)` to `stripDocsPrefix(String path, String version)` using `docParser.docsPrefix(version)`. Update all call sites within `refreshVersion()`.
- **`SearchService`** — remove `buildIndex(String version)` method entirely. `getOrBuildIndex()` returns null if index not in store.
- **`FileSearchResult`** — add `public String extension` field. Update `@AllArgsConstructor`.
- **`SectionSearchResult`** — add `public String extension` field. Update `@AllArgsConstructor`.
- **`CodeSampleSearchResult`** — add `public String extension` field. Update `@AllArgsConstructor`.
- **`ContentSearchResult`** — add `public String extension` field. Update `@AllArgsConstructor`.
- **`SectionContentResult`** — add `public String extension` field. Update constructors.
- **`DocResponse`** — add `public String extension` field. Update `@AllArgsConstructor`.
- **`SqliteSchemaInitializer`** — add `extension TEXT` column to `keyword_files`, `keyword_sections`, `code_samples`, `content_words` tables.
- **`KeywordIndexer.build()`** — accept an additional `String extension` parameter (default `"quarkus-core"` at call sites). Store extension in each indexed entry.
- **`CodeSampleIndexer.build()`** — accept an additional `String extension` parameter. Store extension in each indexed entry.
- **`ContentIndexer.build()`** — accept an additional `String extension` parameter. Store extension in each indexed entry.
- **`SearchResource`** — add optional `@QueryParam("extension") String extension` to all search endpoints. Pass to service layer for filtering.
- **`DocsResource`** — add optional `@QueryParam("extension") String extension` parameter. Populate `extension` field in `DocResponse` (default `"quarkus-core"`). When `extension` query param is provided, validate that the returned doc's path matches the expected extension namespace (e.g., if `extension=quarkus-openapi-generator`, the path should be under `quarkiverse/quarkus-openapi-generator/`).
- **`application.properties`** — change `app.github.repo` to `quarkusio.github.io`, add `app.github.branch=main`, add `app.versions=main` as the non-profile default (dev profile override remains `3.21,3.27,main`).

## Response shape

Updated search result with `extension` field:
```json
{
  "results": [
    {
      "path": "security-overview.adoc",
      "score": 15.0,
      "matchedKeywords": ["security", "oidc"],
      "extension": "quarkus-core"
    }
  ],
  "total": 5,
  "limit": 10,
  "offset": 0,
  "queriedKeywords": ["security", "oidc"],
  "searchTimeMs": 12
}
```

Updated `DocResponse`:
```json
{
  "path": "security-overview.adoc",
  "content": "= Security Overview\n...",
  "format": "asciidoc",
  "extension": "quarkus-core"
}
```

GitHub Contents API response example for `_versions/3.27/guides`:
```json
[
  {
    "name": "security-overview.adoc",
    "path": "_versions/3.27/guides/security-overview.adoc",
    "sha": "abc123..."
  }
]
```

## Tasks

- [ ] Add unit tests for `DocParser.docsPrefix(String version)` default method behavior: calls `docsPrefix("main")`.
- [ ] Add unit tests for `AsciidocParser.docsPrefix(String version)`: returns `"_versions/3.27/guides/"` for version `"3.27"`, `"_versions/main/guides/"` for version `"main"`.
- [ ] Add unit tests for deprecated `AsciidocParser.docsPrefix()`: returns `"_versions/main/guides/"`.
- [ ] Update `DocParser` interface: add `String docsPrefix(String version)` as abstract, change zero-arg to `default` delegating to `docsPrefix("main")`.
- [ ] Implement `AsciidocParser.docsPrefix(String version)`. Mark zero-arg `docsPrefix()` `@Deprecated`. Remove `DOCS_PREFIX` constant.
- [ ] Add unit tests for `ZipDownloadService.extractRelativePath(String entryName, String version)`: correctly strips `_versions/<version>/guides/` prefix.
- [ ] Add unit tests for `ZipDownloadService.streamAndExtractAll(List<String> versions)`: extracts files for multiple versions from a single zip, ignores versions not in the list, returns correct `Map<String, List<String>>`.
- [ ] Refactor `ZipDownloadService.extractRelativePath()` to accept a version parameter.
- [ ] Implement `ZipDownloadService.streamAndExtractAll(List<String> versions)`.
- [ ] Refactor `ZipDownloadService.streamAndExtract(String version)` to delegate to `streamAndExtractAll`.
- [ ] Add unit tests for `GitHubService.fetchZipStream()` (no version param): calls `githubRepositoryClient.fetchZipStream(owner, repo, branch)`.
- [ ] Add unit tests for `GitHubService.fetchIndex(String version)`: calls `githubApiClient.fetchIndex(owner, repo, "_versions/<version>/guides", branch)`.
- [ ] Update `GitHubService`: add `branch` config property, refactor `fetchZipStream()` to no-arg, update `fetchIndex()` to use version-specific docs path and branch as ref.
- [ ] Add unit tests for `CacheWarmupJob`: downloads zip once via `streamAndExtractAll(versions)`, builds indexes per version from result map.
- [ ] Refactor `CacheWarmupJob.onStartup()` to call `streamAndExtractAll(versions)` once, loop result map.
- [ ] Add unit tests for `CacheRefreshJob.stripDocsPrefix(String path, String version)`: strips `_versions/<version>/guides/` prefix correctly.
- [ ] Add unit tests for `CacheRefreshJob.refreshVersion()`: SHA comparison works with new docs path structure and branch ref.
- [ ] Update `CacheRefreshJob`: change `stripDocsPrefix(String path)` to `stripDocsPrefix(String path, String version)`, update all call sites, verify `fetchIndex()` works with new paths.
- [ ] Remove `SearchService.buildIndex(String version)` method. Update `getOrBuildIndex()` to return null when index is absent.
- [ ] Add unit tests verifying `SearchService.getOrBuildIndex()` returns null when no index exists (no lazy download).
- [ ] Add `extension` field to `FileSearchResult`, `SectionSearchResult`, `CodeSampleSearchResult`, `ContentSearchResult`, `SectionContentResult`, `DocResponse`. Update constructors.
- [ ] Add `extension TEXT` column to SQLite schema (`keyword_files`, `keyword_sections`, `code_samples`, `content_words`) in `SqliteSchemaInitializer`.
- [ ] Update `KeywordIndexer.build()`, `CodeSampleIndexer.build()`, `ContentIndexer.build()` to accept `String extension` parameter and store it.
- [ ] Add optional `@QueryParam("extension")` to search endpoints in `SearchResource`. Pass to service methods for filtering.
- [ ] Add optional `@QueryParam("extension") String extension` to `DocsResource.getDoc()`. Add `@Parameter(description = "Optional extension name filter", required = false, example = "quarkus-core")`. Populate `extension` field in `DocResponse` (default `"quarkus-core"`).
- [ ] Update `application.properties`: change `app.github.repo` to `quarkusio.github.io`, add `app.github.branch=main`, add `app.versions=main` as non-profile default.
- [ ] Update WireMock stubs in test resources to serve a zip with `_versions/<version>/guides/` structure instead of `docs/src/main/asciidoc/`.
- [ ] Update all breaking tests:
  - `ZipDownloadServiceTest` — zip entries now use `quarkusio.github.io-main/_versions/<version>/guides/` prefix; mock `gitHubService.fetchZipStream()` (no version arg).
  - `CacheWarmupJobTest` — mock `zipDownloadService.streamAndExtractAll(versions)` instead of per-version `streamAndExtract(version)`.
  - `CacheWarmupJobIntegrationTest` — WireMock zip structure changes to `_versions/<version>/guides/`; single zip download for all versions.
  - `CacheRefreshJobTest` — `GithubApiIndex.path` values change from `docs/src/main/asciidoc/file.adoc` to `_versions/<version>/guides/file.adoc`; `stripDocsPrefix` gains version param.
  - `CacheRefreshJobIntegrationTest` — same path changes as unit test.
  - `SearchServiceTest` — remove tests for lazy `buildIndex()` behavior; add tests confirming null return when index absent.
- [ ] Add integration tests confirming warmup extracts docs from the website repo zip structure.
- [ ] Add integration tests confirming refresh SHA comparison and selective re-fetch works with new paths.
- [ ] Verify all existing search integration tests still pass.

## Operational notes

- This is a breaking change to the cache layout and index structure. All existing cached data becomes stale.
- On first deployment after this feature, set `app.cache-warmup.full-reset=true` to force a complete cache rebuild. The warmup job will download the single website repo zip and extract all configured versions.
- The zip download is now a single ~200MB fetch for all versions instead of one smaller zip per version. This is slower for the initial download but eliminates redundant fetches for shared infrastructure files.
- The `extension` column addition requires a schema migration. `SqliteSchemaInitializer` should handle this via `ALTER TABLE ... ADD COLUMN` with a default value of `"quarkus-core"` for existing rows, or via a full table rebuild if the schema initializer uses `CREATE TABLE IF NOT EXISTS`.
- GitHub API rate limiting: the Contents API call for `_versions/<version>/guides` with `ref=main` counts toward the rate limit. Authenticated requests via `github.token` are recommended for production.

## Implementation notes

_(To be filled during implementation)_
