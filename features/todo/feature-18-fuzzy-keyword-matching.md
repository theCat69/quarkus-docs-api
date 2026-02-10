# Feature 18: Fuzzy/Partial Keyword Matching in Search Endpoints

Add prefix-based keyword matching to file, section, and code sample searches so that partial queries like "secur" match indexed keywords like "security", "secured", and "securing".

## Scope and behavior

- All three keyword search methods (`searchFiles`, `searchSections`, `searchCodeSamples`) gain prefix matching support.
- Prefix matching is always enabled — no query param toggle. AI agent callers benefit from forgiving matching by default; exact-only behavior is approximated by using full words.
- A query keyword matches an indexed keyword if `indexedWord.startsWith(queryKeyword)` (both already lowercased).
- Exact matches retain full score (`ks.score`). Prefix matches apply a `PREFIX_MATCH_MULTIPLIER = 0.8` discount (`ks.score * 0.8`).
- A query keyword that is an exact match takes precedence: if "security" is both an exact and prefix match, use the exact score only.
- The multi-keyword boost (`MULTI_KEYWORD_BOOST = 1.5`) applies to distinct query keywords matched (same logic as today), counting both exact and prefix matches.
- Performance constraint: iteration over `file.keywords` (or `section.keywords`, `sample.keywords`) is already O(k) per entry. Prefix checking adds no algorithmic cost — `startsWith` is O(m) where m is query keyword length.
- `searchContent()` is excluded — it uses substring search on raw text, not keyword indexes.
- No changes to indexing pipeline, SQLite schema, or stored data.

## Internal interfaces

- `SearchService.matchKeywordScore(KeywordScore ks, Set<String> queryKeywords) → double` — returns `ks.score` for exact, `ks.score * 0.8` for prefix, `0.0` for no match. Extract to avoid duplicating the matching logic in three loops.
- Alternatively, a helper `SearchService.computeMatchingScore(List<KeywordScore> keywords, Set<String> queryKeywords) → MatchAccumulator` that returns `(totalScore, matchedCount)` to encapsulate the shared scoring pattern used by `getScores`, `searchSections`, and `searchCodeSamples`.

## Tasks

- [ ] Add unit tests for prefix matching: "secur" matches "security" but not "obscure"; "security" is an exact match, not prefix.
- [ ] Add unit tests verifying the 0.8 multiplier on prefix matches vs 1.0 on exact matches.
- [ ] Add unit tests for edge cases: query keyword equals indexed keyword (exact), query keyword longer than indexed keyword (no match), 2-char query keyword after lowercase (below MIN_TOKEN_LENGTH, still works since validation is at index time).
- [ ] Extract a shared `matchKeywordScore(KeywordScore, Set<String>)` helper in `SearchService` with startsWith logic.
- [ ] Refactor `getScores()` (file search) to use the new prefix-aware matching helper.
- [ ] Refactor `searchSections()` scoring loop to use the new prefix-aware matching helper.
- [ ] Refactor `searchCodeSamples()` scoring loop to use the new prefix-aware matching helper.
- [ ] Add integration tests (`@QuarkusTest`) via `SearchResource` endpoints confirming prefix matching returns results for partial keywords.
- [ ] Add integration test confirming exact keyword scores higher than prefix keyword for the same file.
- [ ] Update OpenAPI descriptions on `/files`, `/sections`, `/code-samples` to mention prefix matching behavior.

