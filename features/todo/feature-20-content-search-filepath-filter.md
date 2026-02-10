# Feature 20: File Path Filtering on Content Search

Add an optional `filePaths` query parameter to the `/api/search/content` endpoint so callers can restrict full-text content search to specific files, consistent with the sections and code samples endpoints.

## Scope and behavior

- Add optional `filePaths` query parameter (comma-separated) to `GET /api/search/content`.
- When provided, restrict content search to only those file paths.
- When omitted, search all files (current behavior).
- Validate with `InputValidator.validateFilePaths()`, same as `/api/search/sections`.
- If Feature 19 (index-based content search) is implemented: filter inverted index results by `filePaths` set before scoring.
- If Feature 19 is not yet implemented: filter the `docStore.listDocFiles()` list before iteration (current brute-force path).
- Both implementations must be supported — the filePaths filter is independent of the indexing strategy.
- Pass `filePaths` as `List<String>` through to `SearchService.searchContent()`.

## Internal interfaces

- `SearchService.searchContent(String version, List<String> keywords, List<String> filePaths, int limit, int offset) → PaginatedResult<ContentSearchResult>` — add `filePaths` parameter (nullable, null means all files).
- `SearchResource.searchContent(...)` — add `@QueryParam("filePaths") String filePaths` with parsing and validation.

## Tasks

- [ ] Add unit test: `searchContent` with `filePaths=["security-overview.adoc"]` returns only results from that file.
- [ ] Add unit test: `searchContent` with `filePaths=null` returns results from all files (existing behavior).
- [ ] Add unit test: `searchContent` with `filePaths` containing a non-matching path returns empty results.
- [ ] Add `filePaths` parameter to `SearchService.searchContent()` method signature.
- [ ] Implement file path filtering in `searchContent()`: if `filePaths` is non-null, filter file candidates before search.
- [ ] Add `@QueryParam("filePaths")` to `SearchResource.searchContent()` with `InputValidator.validateFilePaths()`.
- [ ] Parse comma-separated filePaths string to `List<String>` in the resource, pass to service.
- [ ] Add integration test: `/api/search/content?version=X&keywords=security&filePaths=security-overview.adoc` returns filtered results.
- [ ] Add integration test: `/api/search/content` with invalid filePaths (containing `..`) returns 400.
- [ ] Update OpenAPI `@Parameter` description and `@Operation` description to document the new `filePaths` parameter.

