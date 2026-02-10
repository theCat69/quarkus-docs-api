# Feature 22: File Path Filtering on Content Search

> **Dependencies**: Feature 21 (Index-Based Content Search) should be implemented first. This feature adds file path filtering to the index-based implementation.

Add an optional `filePaths` query parameter to the `/api/search/content` endpoint so callers can restrict full-text content search to specific files, consistent with the sections and code samples endpoints.

## Scope and behavior

- Add optional `filePaths` query parameter (comma-separated) to `GET /api/search/content`.
- When provided, restrict content search to only those file paths.
- When omitted, search all files (current behavior).
- Validate with `InputValidator.validateFilePaths()`, same as `/api/search/sections`.
- Filter inverted index results by `filePaths` set before scoring (Feature 21 provides the index-based implementation).
- Pass `filePaths` as `List<String>` through to `SearchService.searchContent()`.

## Internal interfaces

- `SearchService.searchContent(String version, List<String> keywords, List<String> filePaths, int limit, int offset) → PaginatedResult<ContentSearchResult>` — add `filePaths` parameter (nullable, null means all files).
- `SearchResource.searchContent(...)` — add `@QueryParam("filePaths") String filePaths` with parsing and validation.

## Tasks

- [x] Add unit test: `searchContent` with `filePaths=["security-overview.adoc"]` returns only results from that file.
- [x] Add unit test: `searchContent` with `filePaths=null` returns results from all files (existing behavior).
- [x] Add unit test: `searchContent` with `filePaths` containing a non-matching path returns empty results.
- [x] Add `filePaths` parameter to `SearchService.searchContent()` method signature.
- [x] Implement file path filtering in `searchContent()`: if `filePaths` is non-null, filter inverted index results by file path set before scoring.
- [x] Add `@QueryParam("filePaths")` to `SearchResource.searchContent()` with `InputValidator.validateFilePaths()`.
- [x] Parse comma-separated filePaths string to `List<String>` in the resource, pass to service.
- [x] Add integration test: `/api/search/content?version=X&keywords=security&filePaths=security-overview.adoc` returns filtered results.
- [x] Add integration test: `/api/search/content` with invalid filePaths (containing `..`) returns 400.
- [x] Update OpenAPI `@Parameter` description and `@Operation` description to document the new `filePaths` parameter.

## Implementation notes

- Added `List<String> filePaths` parameter (nullable) to `SearchService.searchContent()` and `searchContentBruteForce()`.
- In index-based path: occurrences with non-matching `occ.filePath` are skipped before grouping/scoring when `filePathSet` is non-null.
- In brute-force fallback: files not in the `filePathSet` are skipped in the iteration loop.
- `SearchResource.searchContent()` parses comma-separated `filePaths` query param to `List<String>`, validates with `InputValidator.validateFilePaths()`.
- Updated OpenAPI `@Operation` and `@APIResponse` descriptions to document the new parameter.
- 13 existing unit tests updated to pass `null` as third argument (filePaths). 3 new unit tests + 2 new integration tests added.
- Pattern follows `searchSections()` which already had `filePaths` filtering.
