# Feature 21: Fuzzy Section Title Matching in Code Sample Search

Replace the exact `equalsIgnoreCase` section title filter in `searchCodeSamples` with `FuzzyMatcher.bestMatch()`, making section title filtering consistent with `getSectionContent` and more forgiving for AI agent callers.

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

- `FuzzyMatcher.bestMatch(String query, List<String> candidates)` — already exists, reused as-is.
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

- [ ] Add unit test: `searchCodeSamples` with exact section title still matches (backward compatibility).
- [ ] Add unit test: `searchCodeSamples` with partial/fuzzy section title (e.g., "Auth" matching "Authentication") returns correct samples.
- [ ] Add unit test: `searchCodeSamples` with section title below threshold returns empty results.
- [ ] Add unit test: `matchedSectionTitle` and `sectionMatchScore` are populated correctly when sectionTitle filter is used.
- [ ] Add unit test: `matchedSectionTitle` is null when sectionTitle filter is not provided.
- [ ] Add `matchedSectionTitle` (String) and `sectionMatchScore` (double) fields to `CodeSampleSearchResult` DTO.
- [ ] Update `CodeSampleSearchResult` `@AllArgsConstructor` to include new fields (add new constructor or update existing).
- [ ] Refactor `SearchService.searchCodeSamples()`: collect unique section titles from candidates, run `FuzzyMatcher.bestMatch()`, filter by matched title.
- [ ] Populate `matchedSectionTitle` and `sectionMatchScore` in returned `CodeSampleSearchResult` instances.
- [ ] Add integration test via `/api/search/code-samples?sectionTitle=Auth` confirming fuzzy match works end-to-end.
- [ ] Update OpenAPI `@Operation` description on `/code-samples` to document fuzzy section title matching behavior.

