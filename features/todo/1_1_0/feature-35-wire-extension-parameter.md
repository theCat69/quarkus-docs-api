# Feature 35: Wire Extension Parameter in Search Endpoints

> **Dependencies**: Best implemented after Feature 32 (Remove Content Search). If implemented before Feature 32, also wire the `extension` parameter on the `/content` endpoint.

The `extension` query parameter exists on all search endpoints in `SearchResource` but is never passed to `SearchService`. Wire it through so that search results can be filtered by extension name (e.g., `extension=quarkus-core` or `extension=quarkus-openapi-generator`).

## Scope and behavior

- Pass `extension` from `SearchResource` to `SearchService` on all search endpoints: `searchFiles()`, `searchSections()`, `searchCodeSamples()` (and `searchContent()` if Feature 32 has not been implemented yet).
- Add `String extension` parameter to:
  - `SearchService.searchFiles(String version, List<String> keywords, int limit, int offset)` → add `String extension`.
  - `SearchService.searchSections(String version, List<String> keywords, List<String> filePaths, int limit, int offset)` → add `String extension` (and `String sectionTitle` if Feature 33 is done).
  - `SearchService.searchCodeSamples(String version, List<String> keywords, String filePath, String sectionTitle, int limit, int offset)` → add `String extension`.
- Filter in the result collection loop: if `extension` is non-null and non-blank, skip entries where `file.extension` (or `sample.extension`) doesn't equal the provided extension.
- Apply filter BEFORE pagination (early skip during iteration) — this ensures `total` count reflects filtered results.
- `null` or blank `extension` = no filter (all extensions included). This preserves backward compatibility.
- No validation on extension value — just string equality match. If the extension doesn't exist, results are simply empty.

## Internal interfaces

- **`SearchService.searchFiles()`** — add `String extension` parameter. Filter in `getFileResults()` or the calling method.
- **`SearchService.searchSections()`** — add `String extension` parameter. Filter in the file iteration loop.
- **`SearchService.searchCodeSamples()`** — add `String extension` parameter. Filter in the sample iteration loop.
- **`SearchResource`** — pass `extension` to all 3 service methods.
  > **Note**: If Feature 33 (Section Search Overhaul) is implemented concurrently, `searchSections()` will also gain a `String sectionTitle` parameter. Coordinate to avoid signature conflicts.

## Response shape

No structural changes. When `extension` is provided, only results matching that extension are returned. The `total` count reflects the filtered count.

## Tasks

- [ ] Add `String extension` parameter to `SearchService.searchFiles()`. Add extension filtering in the file loop.
- [ ] Add `String extension` parameter to `SearchService.searchSections()`. Add extension filtering.
- [ ] Add `String extension` parameter to `SearchService.searchCodeSamples()`. Add extension filtering.
- [ ] Update `SearchResource.searchFiles()` — pass `extension` to service.
- [ ] Update `SearchResource.searchSections()` — pass `extension` to service.
- [ ] Update `SearchResource.searchCodeSamples()` — pass `extension` to service.
- [ ] Add unit tests for extension filtering in `SearchServiceTest`:
  - `searchFiles` with `extension="quarkus-core"` returns only core results.
  - `searchFiles` with `extension="quarkus-openapi-generator"` returns only quarkiverse results.
  - `searchFiles` with `extension=null` returns all results.
  - `searchFiles` with `extension=""` (blank) returns all results.
  - `searchFiles` with `extension="nonexistent"` returns empty results.
  - Same patterns for `searchSections` and `searchCodeSamples`.
- [ ] Add integration tests in `SearchResourceTest`:
  - `/api/search/files?keywords=security&extension=quarkus-core` returns only core results.
  - `/api/search/files?keywords=security` (no extension) returns all results.
- [ ] Update existing `SearchServiceTest` calls to include the new `extension` parameter (pass `null` for backward compat).
- [ ] Run all tests (`./gradlew test`) — all must pass.

## Acceptance Criteria

1. `extension` query parameter is wired from `SearchResource` to `SearchService` on all 3 search endpoints.
2. When `extension` is provided, only results with matching `extension` field are returned.
3. `null`/blank extension returns all results (backward compatible).
4. `total` count in paginated response reflects filtered results.
5. All existing tests pass.

## Operational notes

- Extension filtering is a simple string equality check. No fuzzy matching or case-insensitive comparison.
- The filter runs in the iteration loop before adding to the results list, so it's efficient (no post-processing).
- Common extension values: `"quarkus-core"` for core docs, extension repo names (e.g., `"quarkus-openapi-generator"`) for quarkiverse docs.
