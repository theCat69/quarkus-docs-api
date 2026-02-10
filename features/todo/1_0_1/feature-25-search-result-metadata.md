# Feature 25: Search Result Metadata Enrichment

> **Dependencies**: Feature 19 (Unify Search Algorithm) establishes matchedCount tracking in all search methods. Feature 20 (Fuzzy Matching) adds prefix matching. This feature extends the tracking to collect matched keyword details.

Add matched keyword lists to all search result DTOs and query metadata to `SearchResponse` so that callers understand why results matched and how long the search took.

> **Note**: The `matchedCount` variable introduced by Feature 19 in searchSections and searchContent can be reused. The `matchedKeywords` list naturally provides this count via `.size()`.

## Scope and behavior

- Add `matchedKeywords` field (`List<String>`) to `FileSearchResult`, `SectionSearchResult`, `CodeSampleSearchResult`, and `ContentSearchResult`.
- `matchedKeywords` contains the distinct query keywords (lowercased) that contributed to the result's score.
- If Feature 20 (prefix matching) is implemented, `matchedKeywords` contains the query keywords that matched (not the indexed keywords they matched against).
- Add `matchCount` field (`int`) to `ContentSearchResult` — total number of keyword occurrences found across all matched keywords for that file.
- Add `queriedKeywords` field (`List<String>`) to `SearchResponse` — echo of the input keywords as parsed and lowercased.
- Add `searchTimeMs` field (`long`) to `SearchResponse` — wall-clock milliseconds for the search operation (measured in `SearchResource` around the service call).
- All new fields are additive — existing fields unchanged. Backward compatible for all clients.
- `matchedKeywords` is collected during the existing scoring loops: when a keyword matches (exact or prefix), add it to a per-result set.
- `searchTimeMs` is measured in the resource layer (not service layer) to include serialization-independent overhead.

## Internal interfaces

- `FileSearchResult` — add `public List<String> matchedKeywords`.
- `SectionSearchResult` — add `public List<String> matchedKeywords`.
- `CodeSampleSearchResult` — add `public List<String> matchedKeywords`.
- `ContentSearchResult` — add `public List<String> matchedKeywords` and `public int matchCount`.
- `SearchResponse<T>` — add `public List<String> queriedKeywords` and `public long searchTimeMs`.
- **Constructor strategy for `SearchResponse`**: Remove `@AllArgsConstructor` and add explicit constructors instead. Keep the existing convenience constructor `SearchResponse(List<T> results)` for backward compatibility. Add a new full constructor with all fields including `queriedKeywords` and `searchTimeMs`. Update all call sites in `SearchResource` to use the new full constructor. The `@NoArgsConstructor` annotation stays for Jackson deserialization.
- `SearchResource` methods wrap service calls with `System.nanoTime()` timing and pass `queriedKeywords` + `searchTimeMs` to `SearchResponse`.

## Response shape

Updated `SearchResponse`:
```json
{
  "results": [
    {
      "path": "security-overview.adoc",
      "score": 15.0,
      "matchedKeywords": ["security", "oidc"]
    }
  ],
  "total": 5,
  "limit": 10,
  "offset": 0,
  "queriedKeywords": ["security", "oidc"],
  "searchTimeMs": 12
}
```

Updated `ContentSearchResult`:
```json
{
  "path": "security-overview.adoc",
  "snippet": "...configuring security...",
  "matchOffset": 1423,
  "matchLine": 42,
  "score": 8.5,
  "matchedKeywords": ["security"],
  "matchCount": 14
}
```

## Tasks

- [ ] Add unit tests: `FileSearchResult` includes `matchedKeywords` with correct keywords after search.
- [ ] Add unit tests: `SectionSearchResult` includes `matchedKeywords` for multi-keyword match.
- [ ] Add unit tests: `CodeSampleSearchResult` includes `matchedKeywords`.
- [ ] Add unit tests: `ContentSearchResult` includes `matchedKeywords` and `matchCount`.
- [ ] Add unit tests: single-keyword match produces `matchedKeywords` with one entry; multi-keyword produces multiple.
- [ ] Add `matchedKeywords` field to `FileSearchResult` DTO; update constructors.
- [ ] Add `matchedKeywords` field to `SectionSearchResult` DTO; update constructors.
- [ ] Add `matchedKeywords` field to `CodeSampleSearchResult` DTO; update constructors.
- [ ] Add `matchedKeywords` and `matchCount` fields to `ContentSearchResult` DTO; update constructors.
- [ ] Refactor `SearchService.getScores()` to collect matched keywords per file alongside score accumulation.
- [ ] Refactor `SearchService.searchSections()` scoring loop to collect matched keywords per section.
- [ ] Refactor `SearchService.searchCodeSamples()` scoring loop to collect matched keywords per sample.
- [ ] Refactor `SearchService.searchContent()` to collect matched keywords and total match count per file.
- [ ] Add `queriedKeywords` and `searchTimeMs` fields to `SearchResponse` DTO; replace `@AllArgsConstructor` with explicit constructors — keep existing convenience constructor `SearchResponse(List<T>)` for backward compat, add new full constructor with all fields including metadata.
- [ ] Update `SearchResource.searchFiles()` to measure timing and pass metadata to `SearchResponse`.
- [ ] Update `SearchResource.searchSections()` to measure timing and pass metadata to `SearchResponse`.
- [ ] Update `SearchResource.searchCodeSamples()` to measure timing and pass metadata to `SearchResponse`.
- [ ] Update `SearchResource.searchContent()` to measure timing and pass metadata to `SearchResponse`.
- [ ] Add integration tests confirming `queriedKeywords` and `searchTimeMs` are present in JSON response.
- [ ] Add integration tests confirming `matchedKeywords` is present in result items.
- [ ] Update OpenAPI descriptions to document new response fields.
