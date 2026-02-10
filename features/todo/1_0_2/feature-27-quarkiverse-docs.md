# Feature 27: Quarkiverse Documentation Ingestion

> **Dependencies**: Feature 26 (Switch Doc Source to Website Repository) must be completed first. Feature 26 introduces the `extension` field on all DTOs and SQLite tables, which this feature leverages to distinguish quarkiverse docs from core docs. Additionally, a Jackson YAML dependency must be added to `build.gradle`.

Download and index Quarkiverse extension documentation by parsing the `antora-playbook.yml` from `quarkiverse/quarkiverse-docs`. For each content source listed in the playbook, download the extension's repository zip, extract documentation files, cache them under a namespaced path, and index them alongside the `"main"` version core docs.

> **Note**: Quarkiverse docs are cached and indexed under `"main"` version ONLY. They are NOT available for versioned queries like `"3.27"`. The `extension` field (introduced in Feature 26) distinguishes quarkiverse results from core docs in search responses.

## Scope and behavior

- Parse `antora-playbook.yml` from `https://github.com/quarkiverse/quarkiverse-docs` (fetched via GitHub API using `GithubApiClient.fetchFile()`, base64-decoded) to discover content sources.
- Each content source in the playbook has `url` (repo URL), `branches` (list or pattern), and `start_path` (relative path to the docs root). Example:
  ```yaml
  content:
    sources:
      - url: https://github.com/quarkiverse/quarkus-openapi-generator
        branches: main
        start_path: docs
  ```
- **Branch resolution**: For each content source, resolve the branch to download. A "concrete" branch is one with no `*`, `?`, or regex metacharacters. If `branches` is a string, use it directly if concrete. If `branches` is a list, pick the first concrete entry. If no concrete branch is found, fallback to `main`.
- Doc path inside each extension zip: `<start_path>/modules/ROOT/pages/*.adoc` (standard Antora module layout). Only `ROOT` module is supported; other modules (e.g., `modules/reference/pages/`) are logged as warnings and skipped.
- Quarkiverse docs are **NOT versioned** — cached and indexed under `"main"` version ONLY.
- Cache layout: `.cache/main/docs/quarkiverse/<ext-name>/<file>.adoc` where `<ext-name>` is derived from the repo name (e.g., `quarkus-openapi-generator`).
- `DocStore.read("main", "quarkiverse/<ext>/file.adoc")` works with the existing `DocStore` implementation — no changes needed to `DocStore` because it resolves paths relative to `.cache/<version>/docs/`.
- **Merge strategy**: After core warmup for `"main"`, append quarkiverse file paths to the core file list, then call indexers with the MERGED list. Each file's `extension` value is based on its source: `"quarkus-core"` for core docs, `"<ext-name>"` (e.g., `"quarkus-openapi-generator"`) for quarkiverse docs. The indexers already accept an `extension` parameter per Feature 26.
- Searchable ONLY through `"main"` version. Searching version `"3.27"` does NOT include quarkiverse results.
- Quarkiverse files are **NOT** included in `IndexResource.getIndex()` responses — that endpoint returns core-only GitHub API index data.
- **Parallel downloads**: Extension repo zips are downloaded concurrently using Quarkus `ManagedExecutor` (SmallRye) instead of raw `ExecutorService`. Concurrency is configurable via `app.quarkiverse.download-concurrency` (default `4`).
- **CacheRefreshJob with SHA comparison**: For each quarkiverse extension, use `IndexStore` with composite keys like `"quarkiverse/quarkus-openapi-generator"` to store the GitHub file index (SHA data) for that extension's doc directory. During refresh, fetch the current file index via `GitHubService.fetchIndexForRepo(owner, repo, docsPath, branch)`, compare SHAs with the stored index, and only re-fetch changed files. Rebuild `"main"` indexes if any extension had changes.
- Add config key `app.quarkiverse.enabled` (default `true`) to toggle quarkiverse ingestion.
- Add config key `app.quarkiverse.playbook-repo` (default `quarkiverse/quarkiverse-docs`) and `app.quarkiverse.playbook-branch` (default `main`).
- Add config key `app.quarkiverse.download-concurrency` (default `4`).
- Add Jackson YAML dependency to `build.gradle`: `implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-yaml'`.
- Error handling: if a single extension repo fails to download or extract, log the error and continue. Do not fail the entire warmup/refresh.
- Log progress (e.g., `"Processing extension 15/120: quarkus-openapi-generator"`).

## Internal interfaces

- **`AntoraPlaybook`** — new DTO in `com.fvd.quarkiverse.models`:
  - `public ContentConfig content` where `ContentConfig` has `public List<ContentSource> sources`.
  - `ContentSource`: `public String url`, `public Object branches`, `public String startPath` (mapped from `start_path` via `@JsonProperty`).
- **`AntoraPlaybookParser`** — new utility in `com.fvd.quarkiverse.parser`:
  - `List<ResolvedContentSource> parse(String yamlContent)` — parses YAML using Jackson `ObjectMapper` with `YAMLFactory`, resolves branches per the concrete-branch-first rule.
  - `ResolvedContentSource` record: `(String org, String repo, String branch, String startPath, String extensionName)`. The `extensionName` is the repo name (e.g., `quarkus-openapi-generator`).
- **`QuarkiverseService`** — new `@ApplicationScoped` in `com.fvd.quarkiverse.services`:
  - `List<String> fetchAndExtractAll()` — fetches playbook via `GitHubService.fetchFileContentForRepo()`, parses it, iterates sources with `ManagedExecutor` (concurrency = `app.quarkiverse.download-concurrency`), downloads zips via `GitHubService.fetchZipStreamForRepo()`, extracts docs via `QuarkiverseZipExtractor`, returns all extracted file paths (namespaced as `quarkiverse/<ext-name>/<file>.adoc`).
  - `boolean refreshAll()` — for each extension, fetches GitHub file index at `<startPath>/modules/ROOT/pages` via `GitHubService.fetchIndexForRepo()`, compares SHAs with stored index in `IndexStore` (using composite key `"quarkiverse/<ext-name>"`), re-fetches only changed files via `GitHubService.fetchFileContentForRepo()`, updates stored index. Returns `true` if any extension had changes (signals "main" index rebuild needed).
- **`QuarkiverseZipExtractor`** — new utility in `com.fvd.quarkiverse.services`:
  - `List<String> extractDocs(InputStream zipStream, String extensionName, String startPath, CacheService cacheService)` — extracts `.adoc` files from `<startPath>/modules/ROOT/pages/` in the zip, writes to `.cache/main/docs/quarkiverse/<extensionName>/`. Returns list of namespaced relative paths.
- **`GitHubService`** — add generic methods for arbitrary repos:
  - `InputStream fetchZipStreamForRepo(String owner, String repo, String branch)` — calls `githubRepositoryClient.fetchZipStream(owner, repo, branch)`.
  - `List<GithubApiIndex> fetchIndexForRepo(String owner, String repo, String docsPath, String branch)` — calls `githubApiClient.fetchIndex(owner, repo, docsPath, branch)`.
  - `GithubApiFile fetchFileContentForRepo(String owner, String repo, String filePath, String branch)` — calls `githubApiClient.fetchFile(owner, repo, filePath, branch)`.
- **`CacheWarmupJob`** — after core warmup for `"main"`, if `app.quarkiverse.enabled`, call `quarkiverseService.fetchAndExtractAll()`, merge returned paths with core `"main"` file list, and rebuild `"main"` indexes with the merged list (passing per-file extension names to indexers).
- **`CacheRefreshJob`** — after core refresh, if `app.quarkiverse.enabled`, call `quarkiverseService.refreshAll()`. If it returns `true`, rebuild `"main"` indexes (merging core and quarkiverse file lists).
- **`build.gradle`** — add `implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-yaml'`.

## Response shape

No new endpoints. Quarkiverse docs appear in existing search results with their `extension` field set to the extension name:

```json
{
  "results": [
    {
      "path": "quarkiverse/quarkus-openapi-generator/index.adoc",
      "score": 12.0,
      "matchedKeywords": ["openapi", "generator"],
      "extension": "quarkus-openapi-generator"
    },
    {
      "path": "security-overview.adoc",
      "score": 10.5,
      "matchedKeywords": ["security"],
      "extension": "quarkus-core"
    }
  ],
  "total": 2,
  "limit": 10,
  "offset": 0,
  "queriedKeywords": ["openapi", "generator"],
  "searchTimeMs": 15
}
```

Doc retrieval for quarkiverse files:
```json
GET /api/doc?version=main&path=quarkiverse/quarkus-openapi-generator/index.adoc

{
  "path": "quarkiverse/quarkus-openapi-generator/index.adoc",
  "content": "= Quarkus OpenAPI Generator\n...",
  "format": "asciidoc",
  "extension": "quarkus-openapi-generator"
}
```

## Tasks

- [ ] Add `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` to `build.gradle`.
- [ ] Add unit tests for `AntoraPlaybookParser.parse()`: parses YAML, resolves single concrete branch, handles list (picks first concrete), handles wildcard/regex (fallback to `main`), handles `start_path` mapping.
- [ ] Create `AntoraPlaybook`, `ContentConfig`, `ContentSource` DTOs in `com.fvd.quarkiverse.models`.
- [ ] Create `ResolvedContentSource` record in `com.fvd.quarkiverse.parser`.
- [ ] Implement `AntoraPlaybookParser`.
- [ ] Add unit tests for `QuarkiverseZipExtractor.extractDocs()`: extracts `.adoc` from `<startPath>/modules/ROOT/pages/`, ignores non-adoc files, ignores non-ROOT modules (logs warning), writes to namespaced `quarkiverse/<ext>/` paths.
- [ ] Implement `QuarkiverseZipExtractor`.
- [ ] Add unit tests for `GitHubService.fetchZipStreamForRepo()`: calls `githubRepositoryClient.fetchZipStream(owner, repo, branch)` with correct params.
- [ ] Add unit tests for `GitHubService.fetchIndexForRepo()`: calls `githubApiClient.fetchIndex(owner, repo, docsPath, branch)` with correct params.
- [ ] Add unit tests for `GitHubService.fetchFileContentForRepo()`: calls `githubApiClient.fetchFile(owner, repo, filePath, branch)` with correct params.
- [ ] Add `fetchZipStreamForRepo()`, `fetchIndexForRepo()`, `fetchFileContentForRepo()` to `GitHubService`.
- [ ] Add unit tests for `QuarkiverseService.fetchAndExtractAll()`: fetches playbook, downloads zips in parallel (concurrency 4 via `ManagedExecutor`), extracts docs, returns aggregated file list with namespaced paths.
- [ ] Add unit tests for `QuarkiverseService` error handling: single extension failure doesn't abort others, logs error and continues.
- [ ] Add unit tests for `QuarkiverseService.refreshAll()`: SHA comparison per extension using composite key in `IndexStore`, re-fetches only changed files, returns `true` when changes detected.
- [ ] Implement `QuarkiverseService`.
- [ ] Add config keys to `application.properties`: `app.quarkiverse.enabled=true`, `app.quarkiverse.playbook-repo=quarkiverse/quarkiverse-docs`, `app.quarkiverse.playbook-branch=main`, `app.quarkiverse.download-concurrency=4`.
- [ ] Add unit tests for `CacheWarmupJob` with quarkiverse enabled: after core warmup for `"main"`, calls `quarkiverseService.fetchAndExtractAll()`, merges paths, rebuilds `"main"` indexes.
- [ ] Update `CacheWarmupJob` to call quarkiverse after core warmup.
- [ ] Add unit tests for `CacheRefreshJob` with quarkiverse enabled: after core refresh, calls `quarkiverseService.refreshAll()`, rebuilds `"main"` indexes if changes detected.
- [ ] Update `CacheRefreshJob` to call quarkiverse refresh.
- [ ] Add WireMock stubs for playbook YAML (base64-encoded via GitHub Contents API) and sample extension zip files.
- [ ] Add integration tests: quarkiverse docs appear in `version=main` search results with correct `extension` field.
- [ ] Add integration tests: quarkiverse docs do NOT appear for `version=3.27`.
- [ ] Add integration tests: `/api/doc?version=main&path=quarkiverse/<ext>/index.adoc` returns 200 with content.
- [ ] Add integration tests: `/api/index?version=main` does NOT include quarkiverse file paths (core-only).
- [ ] Disable quarkiverse in test profile by default (`%test.app.quarkiverse.enabled=false`) except in quarkiverse-specific integration tests.

## Operational notes

- First deployment with quarkiverse enabled will download ~120 extension repo zips (approximately). This is a one-time operation; subsequent refreshes only re-fetch changed files.
- **GitHub API rate limiting**: Each extension's playbook fetch, zip download, and file index fetch counts toward the GitHub API rate limit. Authenticated requests via `github.token` are strongly recommended for production. Unauthenticated rate limit is 60 requests/hour; authenticated is 5,000 requests/hour.
- Concurrency is capped at `app.quarkiverse.download-concurrency` (default 4) to avoid overwhelming GitHub's API. Increase cautiously.
- The `ManagedExecutor` from SmallRye integrates with Quarkus's lifecycle and CDI context propagation, which is preferred over raw `ExecutorService`.
- Failed extension downloads do not block other extensions or core doc processing. Check logs for `"Failed to process extension"` warnings.
- Quarkiverse doc paths in the cache are namespaced under `quarkiverse/<ext-name>/` to avoid filename collisions with core docs.
- The `IndexStore` composite key pattern (`"quarkiverse/<ext-name>"`) reuses the existing `version` column semantics — the stored `GithubApiIndex` entries for each extension enable SHA-based incremental refresh.

## Implementation notes

_(To be filled during implementation)_
