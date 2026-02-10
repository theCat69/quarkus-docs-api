# Feature 29: Documentation Updates (README, AGENTS.md, OpenAPI Annotations)

> **Dependencies**: Features 26, 27, and 28 must all be completed first. This feature is implemented LAST in v1.0.2 and documents the final post-v1.0.2 state of the project.

Update all project documentation to reflect the complete v1.0.2 state: rewrite the README from Quarkus boilerplate into a comprehensive project README, update AGENTS.md to reflect new packages/dependencies/config/patterns, and ensure all OpenAPI annotations are accurate for the post-v1.0.2 API surface.

> **Note**: This is a documentation-only feature. No production logic, services, or tests are changed. All code from Features 26-28 is already in place.

## Scope and behavior

### README.md — Full rewrite

Replace the default Quarkus boilerplate with a comprehensive project README covering:

1. **Project Title & Badges** — `quarkus-docs-api` header with a one-line description: REST API for caching, indexing, and searching Quarkus documentation (core and quarkiverse extensions).
2. **Overview** — 2-3 paragraph summary: what the project does (caches Quarkus docs from `quarkusio.github.io`, builds keyword/section/code-sample/full-text indexes in SQLite, provides search API), who it's for (AI agents, tooling, IDEs, MCP servers), key capabilities (multi-version, quarkiverse extensions, fuzzy matching, stemming, pagination).
3. **Architecture** — High-level description of the system: data flow from GitHub website repo → zip download → cache extraction → SQLite indexing → REST API. Mention: single zip for all versions, quarkiverse playbook parsing, background refresh with SHA-based incremental updates. List the main packages: `asciidocs`, `cache`, `common`, `docs`, `github`, `indexs`, `search`, `quarkiverse`.
4. **API Endpoints** — Table or list of all 8 endpoints with method, path, summary, key parameters. Group by resource (`/api/doc`, `/api/index`, `/api/search/*`). Note that `version` is optional (defaults to `main`), and `extension` is an optional filter on all search endpoints.
5. **Quick Start** — Prerequisites (Java 21, Gradle wrapper included), clone, run in dev mode (`./gradlew quarkusDev`), access OpenAPI UI (`/q/swagger-ui`), example curl requests for search and doc retrieval.
6. **Configuration** — Two tables:
   - **Core config keys** with defaults and descriptions: `app.cache.dir`, `app.github.owner`, `app.github.repo`, `app.github.branch`, `app.versions`, `app.refresh.interval`, `app.cache-warmup.full-reset`, `app.quarkiverse.enabled`, `app.quarkiverse.playbook-repo`, `app.quarkiverse.playbook-branch`, `app.quarkiverse.download-concurrency`, SQLite datasource keys, REST client URL keys.
   - **Advanced / Search Tuning** subsection with all `search.*` keys: `search.boost.filename-boost` (default 10), `search.boost.title-boost` (default 5), `search.boost.import-boost` (default 5), `search.boost.section-title-boost` (default 5), `search.boost.multi-keyword-boost` (default 1.5), `search.boost.prefix-match-multiplier` (default 0.8), `search.fuzzy.levenshtein-weight` (default 0.4), `search.fuzzy.containment-weight` (default 0.35), `search.fuzzy.word-overlap-weight` (default 0.25), `search.fuzzy.default-threshold` (default 0.3), `search.fuzzy.containment-partial-threshold` (default 0.5), `search.fuzzy.word-overlap-keyword-threshold` (default 0.3), `search.index.min-keyword-score` (default 2), `search.index.min-token-length` (default 3), `search.snippet.context-size` (default 100).
7. **Examples** — 3-4 curl examples with sample JSON responses: search files, search with extension filter, get doc content, list versions.
8. **Building & Testing** — Build (`./gradlew build`), test (`./gradlew test`), single test class, dev mode commands.
9. **Technology Stack** — Bullet list of all frameworks and libraries.
10. **License** — Placeholder or existing license reference.

### AGENTS.md — Section updates

Update the following sections to reflect the post-v1.0.2 state:

1. **Project summary** — Add: sources docs from `quarkusio.github.io` website repo (not the Quarkus source repo); supports quarkiverse extension docs via Antora playbook parsing; `version` parameter is optional (defaults to `main`); Jackson YAML for playbook parsing.
2. **src/main/java package map** — Add entry: `src/main/java/com/fvd/quarkiverse`: Quarkiverse extension doc ingestion — playbook parsing, zip extraction, and extension management (subpackages: `models`, `parser`, `services`).
3. **Dependencies of note** — Add: `Jackson YAML (com.fasterxml.jackson.dataformat:jackson-dataformat-yaml)` for Antora playbook parsing.
4. **Configuration** — Add note about new config keys from v1.0.2 (`app.github.branch`, `app.quarkiverse.*` keys).
5. **Resource example** in code examples — Show `InputValidator.resolveVersion(version)` pattern, optional `extension` query param, `@Parameter(required = false)` with `@Schema(defaultValue = "main")` on version param.
6. **POJO/DTO example** in code examples — Add `extension` field to the DTO example.
7. **Service example** in code examples — Remove stale `ensureIndex()` / `zipDownloadService` lazy-download pattern (removed in F26); show current service pattern that loads from SQLite store only.

### OpenAPI annotations — Accuracy audit

Features 26, 27, and 28 each specify their own OpenAPI annotation changes as tasks. This feature performs a **final audit** to ensure consistency and catch any gaps:

1. **`DocsResource.getDoc()`** — Verify:
   - `path` parameter description updated from `"Full file path in the GitHub repository (e.g. docs/src/main/asciidoc/security-overview.adoc)"` to `"File path relative to the docs directory (e.g. security-overview.adoc)"`. Example updated from `"docs/src/main/asciidoc/security-overview.adoc"` to `"security-overview.adoc"`.
   - `path` parameter description mentions quarkiverse path pattern: `"For quarkiverse extensions, use quarkiverse/<ext-name>/<file>.adoc"`.
   - `version` parameter has `required = false`, `@Schema(defaultValue = "main")`, description includes `"Defaults to 'main' if omitted."`.
   - `extension` query param has `@Parameter` annotation with description and example.
   - `@Operation` description updated to mention website repo source and quarkiverse doc support.
   - 200 response description mentions `extension` field.
2. **`IndexResource.getIndex()`** — Verify:
   - `version` parameter has `required = false`, `@Schema(defaultValue = "main")`, default note in description.
   - `@Operation` description clarifies this returns core docs only (not quarkiverse files).
3. **`SearchResource.searchFiles()`** — Verify:
   - `version` parameter is `required = false` with `@Schema(defaultValue = "main")`, default note, and quarkiverse disclaimer.
   - `extension` query param has `@Parameter(description = "Optional extension name to filter results (e.g. quarkus-openapi-generator for quarkiverse, or quarkus-core for core docs)", required = false, example = "quarkus-core")`.
   - 200 response description mentions `extension` field in results.
4. **`SearchResource.searchSections()`** — Same checks as `searchFiles`.
5. **`SearchResource.getSectionContent()`** — Verify `version` optional with default note and quarkiverse disclaimer. Verify `extension` param if present. Update 200 response description.
6. **`SearchResource.searchCodeSamples()`** — Same checks as `searchFiles`.
7. **`SearchResource.searchContent()`** — Same checks as `searchFiles`.
8. **`SearchResource.listVersions()`** — Verify unchanged (no version param, no extension param).
9. **Generate and validate** — Generate OpenAPI spec (`/q/openapi`) and verify all descriptions, parameter metadata, and response schemas are accurate.

## Internal interfaces

- **`README.md`** — full rewrite (entire file replaced).
- **`AGENTS.md`** — targeted section edits (project summary, package map, dependencies, configuration, code examples).
- **`DocsResource.java`** — OpenAPI annotation text updates only (no logic changes).
- **`IndexResource.java`** — OpenAPI annotation text updates only (no logic changes).
- **`SearchResource.java`** — OpenAPI annotation text updates only (no logic changes).

## Tasks

### README.md

- [ ] Write "Project Title & Badges" section: project name, one-line description.
- [ ] Write "Overview" section: what it does, who it's for, key capabilities (multi-version, quarkiverse, fuzzy matching, stemming, pagination, SQLite indexes).
- [ ] Write "Architecture" section: data flow (GitHub website repo → zip → cache → SQLite → REST API), single-zip multi-version extraction, quarkiverse playbook parsing, background SHA-based refresh. List main packages.
- [ ] Write "API Endpoints" section: table/list of all 8 endpoints with method, path, summary, key params. Note optional version (defaults to `main`) and optional extension filter.
- [ ] Write "Quick Start" section: prerequisites (Java 21), clone, dev mode command, OpenAPI UI URL, example curl for file search and doc retrieval.
- [ ] Write "Configuration" section with two tables: core config keys (app.cache.dir, app.github.owner, app.github.repo, app.github.branch, app.versions, app.refresh.interval, app.cache-warmup.full-reset, app.quarkiverse.enabled, app.quarkiverse.playbook-repo, app.quarkiverse.playbook-branch, app.quarkiverse.download-concurrency, SQLite keys, REST client URL keys) and an "Advanced / Search Tuning" subsection listing all 15 `search.*` config keys with defaults.
- [ ] Write "Examples" section: 3-4 curl examples with sample JSON responses (search files, search with extension filter, get doc content, list versions).
- [ ] Write "Building & Testing" section: build, test, single test class, dev mode commands.
- [ ] Write "Technology Stack" section: bullet list of all frameworks and libraries.
- [ ] Remove all default Quarkus boilerplate content.

### AGENTS.md

- [ ] Update "Project summary" section: add website repo source (`quarkusio.github.io`), quarkiverse extension support via Antora playbook, optional version defaulting to `main`, Jackson YAML dependency.
- [ ] Update "src/main/java package map": add `src/main/java/com/fvd/quarkiverse` entry with description and subpackages (`models`, `parser`, `services`).
- [ ] Update "Dependencies of note": add `Jackson YAML (com.fasterxml.jackson.dataformat:jackson-dataformat-yaml)` line.
- [ ] Update "Configuration" section: add a bullet list of new config keys from v1.0.2 under the existing section: `app.github.branch` (default `main`), `app.quarkiverse.enabled` (default `true`), `app.quarkiverse.playbook-repo` (default `quarkiverse/quarkiverse-docs`), `app.quarkiverse.playbook-branch` (default `main`), `app.quarkiverse.download-concurrency` (default `4`).
- [ ] Update "Resource example" in code examples: show `InputValidator.resolveVersion(version)` pattern, optional `extension` query param, `@Parameter(required = false)` with `@Schema(defaultValue = "main")` on version param.
- [ ] Update "POJO/DTO example" in code examples: add `extension` field to the DTO example.
- [ ] Update "Service example" in code examples: remove stale `ensureIndex()` / `zipDownloadService` lazy-download pattern (removed in F26); show current service pattern that loads from SQLite store only.

### OpenAPI annotation audit

- [ ] Audit `DocsResource.getDoc()`: verify `path` param description and example updated to relative path (`security-overview.adoc`), mention quarkiverse path pattern. Verify `version` param is `required = false` with `@Schema(defaultValue = "main")` and default note. Verify `extension` param present with description and example. Update `@Operation` description to mention website repo and quarkiverse. Update 200 response description to mention `extension` field.
- [ ] Audit `IndexResource.getIndex()`: verify `version` param is `required = false` with `@Schema(defaultValue = "main")` and default note. Update `@Operation` description to clarify core-only (no quarkiverse files).
- [ ] Audit `SearchResource.searchFiles()`: verify `version` optional with default note and quarkiverse disclaimer. Verify `extension` param present with description and example. Update 200 response description to mention `extension` field.
- [ ] Audit `SearchResource.searchSections()`: same checks as `searchFiles`.
- [ ] Audit `SearchResource.getSectionContent()`: verify `version` optional with default note and quarkiverse disclaimer. Verify `extension` param if present. Update 200 response description.
- [ ] Audit `SearchResource.searchCodeSamples()`: same checks as `searchFiles`.
- [ ] Audit `SearchResource.searchContent()`: same checks as `searchFiles`.
- [ ] Verify `SearchResource.listVersions()` is unchanged (no version param, no extension param).
- [ ] Generate OpenAPI spec (`/q/openapi`) and verify all descriptions, parameter metadata, and response schemas are accurate.

## Operational notes

- This feature produces no runtime behavior changes. It is safe to deploy at any time after Features 26-28.
- The README rewrite eliminates the default Quarkus boilerplate, so it is a breaking change for anyone who relied on the boilerplate links — this is intentional.
- The AGENTS.md changes are consumed by AI coding agents. Inaccurate code examples or stale patterns will mislead agents, so accuracy is critical.
- The OpenAPI audit tasks may be no-ops if Features 26, 27, and 28 already made all the annotation changes. The tasks exist as a verification step to catch any gaps or inconsistencies between the three features.
- After this feature, verify the generated OpenAPI spec at `/q/openapi` matches the documented endpoints, parameter optionality, and response shapes.

## Implementation notes

_(To be filled during implementation)_
