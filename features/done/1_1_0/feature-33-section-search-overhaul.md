# Feature 33: Section Search Overhaul with Content Snippets and Keyword-Based Section Content

> **Dependencies**: Feature 32 (Remove Content Search) should be completed first so that `generateSnippet()` and `computeLineNumber()` are already preserved in `SearchService` for reuse here.

Enhance the section search and section content endpoints with three improvements: (1) content snippets in section search results, (2) optional `sectionTitle` fuzzy filter on the `/sections` endpoint, and (3) keyword-based section content lookup on `/section-content`.

## Scope and behavior

### 1. Content snippets in section search results

- Add `snippet` field to `SectionSearchResult` — a ~100 character preview of the section content around the first keyword match.
- Snippet generation reuses `SearchService.generateSnippet()` (already exists from the deleted content search).
- **Performance optimization**: Only generate snippets for the paginated results (the `limit` items returned), not for all matching sections. This means snippet generation happens AFTER sorting and pagination.
- If no keyword match is found within the section content, use the first 100 characters of the section as the snippet.

### 2. Optional sectionTitle filter on /sections endpoint

- Add optional `sectionTitle` query parameter to `/api/search/sections`.
- When provided, filter sections using `FuzzyMatcher.bestMatch()` — only include sections whose title fuzzy-matches the provided `sectionTitle` above the default threshold.
- Add `matchedSectionTitle` and `sectionMatchScore` fields to `SectionSearchResult` — populated when `sectionTitle` filter is active.
- This mirrors the existing `sectionTitle` filter behavior on `/api/search/code-samples`.

### 3. Keyword-based section content lookup

- Add optional `keywords` query parameter to `/api/search/section-content`.
- When `keywords` is provided (and `filePath`/`sectionTitle` are omitted), look up the top-scoring section matching those keywords and return its full content.
- Flow: keywords → `searchSections(version, keywords, null, null, 1, 0)` → take top result → `getSectionContent(version, result.path, result.section)`.
- When both `filePath`+`sectionTitle` AND `keywords` are provided, `filePath`+`sectionTitle` takes precedence (keywords are ignored).
- When neither is provided, return 400.

## Internal interfaces

- **`SectionSearchResult`** — add fields:
  ```java
  public String snippet;              // ~100 char content preview
  public String matchedSectionTitle;  // populated when sectionTitle filter is active
  public double sectionMatchScore;    // 0.0–1.0, populated when sectionTitle filter is active
  ```

- **`SearchService.searchSections()`** — add `String sectionTitle` parameter:
  ```java
  public PaginatedResult<SectionSearchResult> searchSections(String version, List<String> keywords,
          List<String> filePaths, String sectionTitle, int limit, int offset)
  ```
  > **Note**: If Feature 35 (Wire Extension Parameter) is implemented concurrently, `searchSections()` will also need a `String extension` parameter. Coordinate with Feature 35 to avoid signature conflicts.

  - When `sectionTitle` is non-null/non-blank, collect unique section titles from keyword-matched sections, run `FuzzyMatcher.bestMatch(sectionTitle, uniqueTitles)`, filter to only sections matching the fuzzy result.
  - After pagination, generate snippets for the returned items only.

- **`SearchResource.searchSections()`** — add `@QueryParam("sectionTitle") String sectionTitle` parameter with `@Parameter` annotation.

- **`SearchResource.getSectionContent()`** — add `@QueryParam("keywords") String keywords` parameter:
  - If `filePath` and `sectionTitle` are provided, use existing behavior (ignore keywords).
  - If `keywords` is provided (and filePath/sectionTitle are null/blank), call `searchSections` with limit=1, take top result, then call `getSectionContent` with result's path and section title.
  - If neither path nor keywords provided, throw 400.

## Response shape

### Section search with snippet
```json
{
  "results": [
    {
      "path": "security-overview.adoc",
      "section": "Authentication",
      "start": 15,
      "end": 42,
      "score": 12.5,
      "matchedKeywords": ["security", "oidc"],
      "extension": "quarkus-core",
      "snippet": "...Quarkus provides comprehensive security features including OIDC authentication and authorization...",
      "matchedSectionTitle": null,
      "sectionMatchScore": 0.0
    }
  ]
}
```

### Section search with sectionTitle filter
```json
{
  "results": [
    {
      "path": "security-overview.adoc",
      "section": "Authentication and Authorization",
      "start": 15,
      "end": 42,
      "score": 12.5,
      "matchedKeywords": ["security"],
      "extension": "quarkus-core",
      "snippet": "...security features including OIDC authentication...",
      "matchedSectionTitle": "Authentication and Authorization",
      "sectionMatchScore": 0.85
    }
  ]
}
```

## Tasks

- [x] Add `snippet`, `matchedSectionTitle`, `sectionMatchScore` fields to `SectionSearchResult`. Update constructor.
- [x] Add unit tests for snippet generation in section search: snippet is ~100 chars around first keyword match within section content.
- [x] Add unit tests for sectionTitle fuzzy filtering on section search.
- [x] Add unit tests for keyword-based section content lookup.
- [x] Update `SearchService.searchSections()` — add `sectionTitle` parameter, implement fuzzy title filtering, add post-pagination snippet generation.
- [x] Add private helper `generateSectionSnippet(String version, SectionSearchResult result, Set<String> keywords)` in `SearchService` — reads section content from `docStore`, finds first keyword occurrence, calls `generateSnippet()`.
- [x] Update `SearchResource.searchSections()` — add `sectionTitle` query parameter, pass to service.
- [x] Update `SearchResource.getSectionContent()` — add `keywords` query parameter, implement keyword-based lookup fallback.
- [x] Add integration tests: section search returns snippets.
- [x] Add integration tests: section search with `sectionTitle` filter returns only matching sections with `matchedSectionTitle` and `sectionMatchScore`.
- [x] Add integration tests: `/section-content?keywords=security` returns the top section's content.
- [x] Add integration tests: `/section-content?filePath=...&sectionTitle=...&keywords=...` ignores keywords and uses filePath+sectionTitle.
- [x] Add integration test: `/section-content` with no params returns 400.
- [x] Update existing section search tests to account for new fields (default values: `snippet=null`, `matchedSectionTitle=null`, `sectionMatchScore=0.0`).
- [x] Run all tests (`./gradlew test`) — all must pass.

## Acceptance Criteria

1. Section search results include a `snippet` field with ~100 char content preview.
2. Snippets are only generated for paginated results (not all matches).
3. Optional `sectionTitle` parameter on `/sections` filters with fuzzy matching.
4. `matchedSectionTitle` and `sectionMatchScore` populated when sectionTitle filter is active.
5. `/section-content` accepts optional `keywords` for keyword-based lookup.
6. `filePath`+`sectionTitle` takes precedence over `keywords` on `/section-content`.
7. Missing both path and keywords on `/section-content` returns 400.
8. All existing tests pass.

## Operational notes

- Snippet generation reads section content from `docStore` — this adds I/O per returned result. Since it's only done for paginated results (default 10), the impact is minimal.
- The `sectionTitle` filter on `/sections` mirrors the existing pattern on `/code-samples`, so the UX is consistent.
- Keyword-based section content is a convenience for AI agents that want to go from "find me the section about security" to "give me its full content" in a single call.

## Implementation notes

- `SectionSearchResult` retains `@AllArgsConstructor`/`@NoArgsConstructor` Lombok annotations. A backward-compatible 7-arg constructor (without the 3 new fields) was added manually, defaulting `snippet=null`, `matchedSectionTitle=null`, `sectionMatchScore=0.0`.
- Feature 35 (Wire Extension Parameter) was already merged, so `searchSections()` already had a `String extension` parameter. The new `String sectionTitle` parameter was inserted between `filePaths` and `extension` to maintain logical grouping.
- `generateSectionSnippet()` guards against `docStore == null` (common in unit tests where docStore isn't injected) — returns `null` in that case.
- All 17 existing `searchSections()` call sites in `SearchServiceTest` were updated to pass `null` for the new `sectionTitle` parameter.
- The `getSectionContent` endpoint parameters (`filePath`, `sectionTitle`) no longer have `required = true` in their `@Parameter` annotations since the endpoint now supports an alternative `keywords`-based lookup path.
- 4 new unit tests added in a `SectionSearchSnippetAndFilterTests` nested class; 5 new integration tests added in `SearchResourceTest`.
- All tests pass (`BUILD SUCCESSFUL`).
