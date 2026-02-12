# Feature 31: Space-Separated Keywords with Query-Time Stop Word Removal

> **Dependencies**: Feature 30 (Extract Stop Words into Shared Constant) must be completed first. This feature uses `StopWords.DEFAULT` for query-time filtering.

Change all search endpoints from comma-separated keyword input (`keywords.split(",")`) to space-separated input (`split("\\s+")`) and add query-time stop word removal. This makes the API more natural for AI agent consumers that send natural language queries like "how does security work in quarkus".

## Scope and behavior

- Add `InputValidator.parseKeywords(String raw)` — a new method that:
  1. Validates the raw input is not null/blank (reuses `requireNonEmpty`).
  2. Splits on whitespace (`raw.trim().split("\\s+")`).
  3. Lowercases each token.
  4. Filters out tokens in `StopWords.DEFAULT`.
  5. Returns `List<String>` of cleaned keywords.
  6. Throws `InvalidInputException("All keywords are stop words")` if the filtered list is empty.
- Update `SearchResource.searchFiles()`: replace `Arrays.asList(keywords.split(","))` with `InputValidator.parseKeywords(keywords)`.
- Update `SearchResource.searchSections()`: same replacement.
- Update `SearchResource.searchCodeSamples()`: same replacement.
- Update `SearchResource.searchContent()`: same replacement (note: this endpoint may be removed by Feature 32 — if so, skip this change).
- Remove the separate `InputValidator.validateKeywords(keywords)` call from each endpoint since `parseKeywords()` includes that validation.
- Update the `queriedKeywords` computation in each endpoint — `parseKeywords()` already returns lowercased keywords, so `queriedKeywords = keywordList` (no separate `.map(String::toLowerCase)` needed).
- Update all `@Parameter` descriptions for `keywords` from `"Comma-separated list of search keywords"` to `"Space-separated search keywords (stop words like 'how', 'does', 'the' are automatically removed)"`.
- Update `@Parameter` examples from `"security,oidc"` to `"security oidc"`.
- **Backward compatibility**: Comma-separated input like `"security,oidc"` will be treated as a single keyword `"security,oidc"` which won't match anything useful. This is an intentional breaking change in v1.1.0.

## Internal interfaces

- **`InputValidator`** — add method:
  ```java
  public static List<String> parseKeywords(String raw) {
      requireNonEmpty(raw, "keywords");
      List<String> filtered = Arrays.stream(raw.trim().split("\\s+"))
              .map(String::toLowerCase)
              .filter(k -> !StopWords.DEFAULT.contains(k))
              .toList();
      if (filtered.isEmpty()) {
          throw new InvalidInputException("All keywords are stop words");
      }
      return filtered;
  }
  ```
- **`SearchResource`** — update all search endpoints to use `parseKeywords()`.

## Response shape

No structural changes. The `queriedKeywords` field in responses will now reflect the stop-word-filtered keywords. For example, a query `"how does security work"` will return `queriedKeywords: ["security"]` (since "how", "does", "work" are all stop words).

## Tasks

- [x] Add unit tests for `InputValidator.parseKeywords()`:
  - Splits on spaces: `"security oidc"` → `["security", "oidc"]`.
  - Lowercases: `"Security OIDC"` → `["security", "oidc"]`.
  - Removes stop words: `"how does security work"` → `["security"]`.
  - Trims and handles multiple spaces: `"  security   oidc  "` → `["security", "oidc"]`.
  - Throws on null/blank input.
  - Throws on all-stop-words input: `"how does the"` → `InvalidInputException`.
  - Single keyword: `"security"` → `["security"]`.
  - Tab/newline splitting: `"security\toidc"` → `["security", "oidc"]`.
- [x] Implement `InputValidator.parseKeywords()`.
- [x] Update `SearchResource.searchFiles()`: replace `validateKeywords` + `split(",")` + `toLowerCase` with `parseKeywords()`.
- [x] Update `SearchResource.searchSections()`: same replacement.
- [x] Update `SearchResource.searchCodeSamples()`: same replacement.
- [x] Update `SearchResource.searchContent()`: same replacement (skip if Feature 32 removes this endpoint first).
- [x] Update all `@Parameter` descriptions and examples for `keywords` parameter.
- [x] Update `SearchResourceTest` — change ALL test keyword parameters from comma-separated to space-separated format. This affects every test method that sends `keywords` query parameters (file search, section search, code-sample search, and content search tests). Search for all occurrences of comma-separated keyword strings (e.g., `"oidc,security"`, `"security,authentication"`) and replace commas with spaces.
- [x] Add integration test: query `"how does security work in quarkus"` returns results for `"security"` and `"quarkus"` with stop words stripped.
- [x] Add integration test: query `"how does the"` returns 400 with error message about all keywords being stop words.
- [x] Run all tests (`./gradlew test`) — all must pass.

## Acceptance Criteria

1. All search endpoints accept space-separated keywords.
2. Stop words from `StopWords.DEFAULT` are removed at query time.
3. `queriedKeywords` in response reflects only the non-stop-word keywords.
4. All-stop-word queries return 400 with a clear error message.
5. OpenAPI descriptions and examples are updated.
6. All existing tests pass (after updating from comma to space format).

## Operational notes

- **Breaking change**: Clients currently sending comma-separated keywords (e.g., `keywords=security,oidc`) must switch to space-separated (e.g., `keywords=security oidc`). URL encoding: spaces become `+` or `%20`.
- Stop word removal is case-insensitive (keywords are lowercased before filtering).
- The `validateKeywords()` method remains available in `InputValidator` for any other future use, but the search endpoints no longer call it directly.

## Implementation notes

- **`InputValidator.parseKeywords()`**: New method that splits on `\s+`, lowercases, filters out `StopWords.DEFAULT`, and throws `InvalidInputException("All keywords are stop words")` if the filtered list is empty.
- **`SearchResource`**: All 4 search endpoints (`searchFiles`, `searchSections`, `searchCodeSamples`, `searchContent`) updated — replaced `validateKeywords()` + `split(",")` + `toLowerCase()` with `parseKeywords()`. Updated `@Parameter` descriptions from "Comma-separated list of search keywords" to "Space-separated search keywords (stop words like 'how', 'does', 'the' are automatically removed)" and examples from `"security,oidc"` to `"security oidc"`.
- **`InputValidatorTest`**: Added 9 unit tests for `parseKeywords()` covering space splitting, lowercasing, stop word removal, multiple spaces, null/blank, all-stop-words error, single keyword, and tab splitting.
- **`SearchResourceTest`**: Changed 2 occurrences of comma-separated keywords (`"oidc,security"` → `"oidc security"`). Added 2 integration tests: stop word removal verification and all-stop-words 400 error.
- **`QuarkiverseIntegrationTest`**: Changed 4 occurrences of comma-separated keywords to space-separated.
- **All 443 tests pass** after changes.
