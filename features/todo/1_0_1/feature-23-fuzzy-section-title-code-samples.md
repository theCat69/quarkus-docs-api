# Feature 23: Fuzzy Section Title Matching in Code Sample Search

> **Dependencies**: Feature 18 (SearchConfig @ConfigMapping) must be implemented first. FuzzyMatcher is now a CDI bean — inject it instead of using static calls. Use SearchConfig.fuzzy().defaultThreshold() for the fuzzy threshold instead of hardcoding 0.3.

Replace the exact `equalsIgnoreCase` section title filter in `searchCodeSamples` with `FuzzyMatcher.bestMatch()`, making section title filtering consistent with `getSectionContent` and more forgiving for AI agent callers.

> **Note**: FuzzyMatcher should be injected as a CDI bean (per Feature 18), not called statically.

## Scope and behavior

- When `sectionTitle` query param is provided in `/api/search/code-samples`, use `FuzzyMatcher.bestMatch()` instead of `equalsIgnoreCase` to filter code samples by section title.
- Collect all unique section titles from candidate code samples (after filePath filtering if applicable), run `FuzzyMatcher.bestMatch(sectionTitle, uniqueTitles)` with threshold `0.3`.
- If fuzzy match succeeds, filter samples to those whose `sample.sectionTitle` equals the matched title.
- If no match exceeds the threshold, return empty results (no samples match the section title).
- Add optional `matchedSectionTitle` and `sectionMatchScore` fields to `CodeSampleSearchResult` DTO to communicate how the section title was resolved.
- These fields are `null` / `0.0` when no `sectionTitle` filter is provided (backward compatible — no change for keyword-only queries).
- When `sectionTitle` is not provided, behavior is unchanged (no section title filtering).
- Keyword scoring and multi-keyword boost remain unchanged.

## Internal interfaces

- `FuzzyMatcher.bestMatch(String query, List<String> candidates)` — already exists, reused as-is. FuzzyMatcher should be injected as a CDI bean (per Feature 18), not called statically.
- `CodeSampleSearchResult` — add `public String matchedSectionTitle` and `public double sectionMatchScore` fields.
- `SearchService.searchCodeSamples(...)` — internal refactor only, no signature change.

## Response shape

Updated `CodeSampleSearchResult`:
```json
{
  "path": "security-oidc.adoc",
  "sectionTitle": "Authentication",
  "matchedSectionTitle": "Authentication",
  "sectionMatchScore": 1.0,
  "language": "java",
  "content": "...",
  "startLine": 45,
  "endLine": 60,
  "score": 12.5
}
```

When `sectionTitle` query param is not provided:
```json
{
  "path": "security-oidc.adoc",
  "sectionTitle": "Authentication",
  "matchedSectionTitle": null,
  "sectionMatchScore": 0.0,
  "language": "java",
  "content": "...",
  "startLine": 45,
  "endLine": 60,
  "score": 12.5
}
```

## Tasks

- [x] Add unit test: `searchCodeSamples` with exact section title still matches (backward compatibility).
- [x] Add unit test: `searchCodeSamples` with partial/fuzzy section title (e.g., "Auth" matching "Authentication") returns correct samples.
- [x] Add unit test: `searchCodeSamples` with section title below threshold returns empty results.
- [x] Add unit test: `matchedSectionTitle` and `sectionMatchScore` are populated correctly when sectionTitle filter is used.
- [x] Add unit test: `matchedSectionTitle` is null when sectionTitle filter is not provided.
- [x] Add `matchedSectionTitle` (String) and `sectionMatchScore` (double) fields to `CodeSampleSearchResult` DTO.
- [x] Update `CodeSampleSearchResult` `@AllArgsConstructor` to include new fields (add new constructor or update existing).
- [x] Refactor `SearchService.searchCodeSamples()`: collect unique section titles from candidates, run `FuzzyMatcher.bestMatch()`, filter by matched title.
- [x] Populate `matchedSectionTitle` and `sectionMatchScore` in returned `CodeSampleSearchResult` instances.
- [x] Add integration test via `/api/search/code-samples?sectionTitle=Auth` confirming fuzzy match works end-to-end.
- [x] Update OpenAPI `@Operation` description on `/code-samples` to document fuzzy section title matching behavior.

## Implementation notes

- Added `matchedSectionTitle` (String) and `sectionMatchScore` (double) fields to `CodeSampleSearchResult` DTO between `sectionTitle` and `language` fields. Uses Lombok `@AllArgsConstructor` for the 9-arg constructor.
- Refactored `SearchService.searchCodeSamples()`: when `sectionTitle` is provided, collects unique section titles from candidates (after filePath filtering), runs `fuzzyMatcher.bestMatch(sectionTitle, uniqueTitles)` with default threshold (0.3). If no match exceeds threshold, returns empty. Otherwise filters samples to matched title only.
- `matchedSectionTitle` and `sectionMatchScore` are `null`/`0.0` when no sectionTitle filter is provided (backward compatible).
- Existing `searchCodeSamplesSectionTitleFilterIsCaseInsensitive` test still passes since FuzzyMatcher treats case-insensitive exact matches as score 1.0.
- Updated `searchCodeSamplesReturnsAllFields` test to also assert the new fields are null/0.0 when no filter.
- 5 new unit tests + 1 integration test added (327 total, all passing).
- OpenAPI `@Operation` description updated to document fuzzy section title matching behavior.
