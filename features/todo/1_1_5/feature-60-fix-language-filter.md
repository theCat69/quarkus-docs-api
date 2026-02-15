# Feature 60: Fix Language Filter on Code Samples

> **Dependencies**: Feature 57 (Fix totalCount Mismatch). Feature 57 moves the language filter into `SearchService`, which is where the fixes in this feature should be applied.

## Summary

The language filter on the `/api/code-samples` endpoint has two issues: (1) code blocks without a `[source,language]` attribute are stored with an empty string `""` as the language, making them invisible to language-filtered searches even though they may contain code in the requested language; and (2) the language value is not normalized during indexing, so `"Java"`, `"java"`, and `"JAVA"` are stored as different values. This feature improves language extraction robustness during indexing and ensures consistent case-normalized storage.

## User Story

As an **AI agent searching for code samples by language**, I want the `language` filter to reliably return all code samples in the requested language so that I can find Java code blocks regardless of whether the AsciiDoc source uses `[source,java]`, `[source,Java]`, or has no `[source]` attribute at all.

## Motivation

### Current Behavior

**Issue 1: Empty language for unattributed code blocks**

In `AsciidocParser.parseCodeBlocks()` (line 180):
```java
String language = pendingLanguage != null ? pendingLanguage : "";
```

When a code block has no `[source,language]` attribute, `pendingLanguage` is `null`, and the language is stored as `""` (empty string). In `CodeSampleService.searchCodeSamples()` (line 59):
```java
if (language != null && !language.isBlank() && !language.equalsIgnoreCase(csResult.language)) {
    continue;
}
```

When `csResult.language` is `""` and the user filters by `language=java`, `"java".equalsIgnoreCase("")` is `false`, so the code block is skipped. This is **correct behavior** -- we should not return unattributed code blocks when a specific language is requested, because we don't know their language.

However, many AsciiDoc files in the Quarkus documentation use code blocks without `[source]` attributes for simple examples, configuration snippets, or shell commands. These blocks are never findable via language filter.

**Issue 2: Case inconsistency in stored language**

In `AsciidocParser.parseCodeBlocks()` (lines 162-164):
```java
pendingLanguage = sourceMatcher.group(1);
if (pendingLanguage != null) {
    pendingLanguage = pendingLanguage.trim();
}
```

The language is stored as-is from the `[source,language]` attribute. The Quarkus documentation uses mixed case: `[source,java]`, `[source,Java]`, `[source,properties]`, `[source,XML]`, `[source,yaml]`, etc. While the filter in `CodeSampleService` uses `equalsIgnoreCase()` (line 59), the inconsistent storage means:
- The `language` field in API responses shows mixed case (`"Java"` vs `"java"`)
- Any future exact-match logic would break

**Issue 3: Language filter and totalCount (covered by Feature 57)**

After Feature 57 is implemented, the language filter will be in `SearchService.searchCodeSamples()`. This feature ensures the language data is clean and the filter logic handles edge cases correctly within `SearchService`.

---

## Requirements

### R1: Normalize Language to Lowercase During Indexing

**File:** `src/main/java/com/fvd/asciidocs/parser/AsciidocParser.java`, method `parseCodeBlocks()` (line 162-164)

**Current code:**
```java
pendingLanguage = sourceMatcher.group(1);
if (pendingLanguage != null) {
    pendingLanguage = pendingLanguage.trim();
}
```

**Updated code:**
```java
pendingLanguage = sourceMatcher.group(1);
if (pendingLanguage != null) {
    pendingLanguage = pendingLanguage.trim().toLowerCase();
}
```

This ensures all stored language values are lowercase (`"java"`, `"properties"`, `"xml"`, `"yaml"`, etc.).

### R2: Normalize Language Filter Input in SearchService

After Feature 57 moves the language filter into `SearchService.searchCodeSamples()`, ensure the filter comparison is case-insensitive or that the filter input is also lowercased:

```java
// In SearchService.searchCodeSamples() -- after Feature 57
String normalizedLanguage = (language != null && !language.isBlank()) ? language.trim().toLowerCase() : null;
// ...
if (normalizedLanguage != null && !normalizedLanguage.equals(sample.language)) {
    continue;
}
```

Since stored values are now always lowercase (R1), we can use `equals()` instead of `equalsIgnoreCase()` for a slight optimization, but we must lowercase the filter input.

### R3: Keep Empty String for Unattributed Code Blocks

**Decision:** Code blocks without a `[source,language]` attribute will continue to store `""` as the language. This is correct because:
- We genuinely don't know the language of an unattributed code block
- Returning unattributed blocks when a user filters by `language=java` would produce false positives
- An unattributed block could be shell, plaintext, configuration, or any language

**No change needed** in `AsciidocParser.parseCodeBlocks()` line 180.

### R4: Verify Language Extraction Handles Edge Cases

**File:** `src/main/java/com/fvd/asciidocs/parser/AsciidocParser.java`

The `SOURCE_ATTRIBUTE` pattern (line 21):
```java
private static final Pattern SOURCE_ATTRIBUTE = Pattern.compile("^\\[source(?:,\\s*([^\\]]+))?\\]$");
```

Verify this correctly handles:

| AsciiDoc Input | Captured Language | After Normalization |
|----------------|-------------------|---------------------|
| `[source,java]` | `java` | `java` |
| `[source, java]` | `java` | `java` (after trim) |
| `[source,Java]` | `Java` | `java` (after toLowerCase) |
| `[source,XML]` | `XML` | `xml` |
| `[source]` | `null` (group 1 not captured) | stored as `""` |
| `[source,]` | `` (empty string) | `` (empty after trim) |
| `----` (no source attribute) | `null` (no match) | stored as `""` |

**Edge case:** `[source,]` (comma but no language). The regex captures empty string. After `trim().toLowerCase()`, this becomes `""`. This is handled correctly -- same as unattributed.

### R5: Update Code Sample API Response to Show Normalized Language

After R1, all new indexes will store lowercase language. **Existing cached indexes** in SQLite will still have mixed-case language values until they are rebuilt. This is acceptable because:
- Index rebuild happens on cache refresh (every 6 hours by default)
- The `invalidateCache()` method in `SearchService` clears the in-memory cache
- A manual re-index via warmup/refresh will produce clean data

### R6: Update Tests

**Tests to update:**

| Test File | Change |
|-----------|--------|
| `AsciidocParserTest` (if exists) | Verify `parseCodeBlocks()` returns lowercase language |
| `CodeSampleIndexerTest` | Verify indexed entries have lowercase language |
| `SearchServiceTest` | Verify language filter works with lowercase stored values |
| `CodeSampleServiceTest` | Verify language filter uses case-insensitive comparison (or both sides lowercase) |

---

## Implementation Notes

### Interaction with Feature 57

Feature 57 moves the language filter from `CodeSampleService.searchCodeSamples()` into `SearchService.searchCodeSamples()`. This feature (60) should be implemented **after** Feature 57, so the language filter logic is modified in `SearchService` rather than `CodeSampleService`.

If Feature 60 is implemented **before** Feature 57, apply the normalization changes to `CodeSampleService.searchCodeSamples()` (line 59) and `AsciidocParser.parseCodeBlocks()` (line 162-164). Feature 57 will then move the normalized filter logic into `SearchService`.

### No Breaking API Changes

The `language` field in `CodeSampleResult` responses will now always be lowercase. This is technically a behavior change, but since language values were already inconsistent (mixed case), consumers should already be doing case-insensitive comparisons. Normalizing to lowercase makes the API more predictable.

### Index Rebuild Required

After deploying this change, existing indexes in SQLite will contain mixed-case language values. A cache refresh or re-index will produce clean lowercase values. The filter will still work with old data because:
- If Feature 57 is done first: the filter in `SearchService` will use `equalsIgnoreCase()` or both sides will be lowercased
- If Feature 60 is done first: the existing `equalsIgnoreCase()` in `CodeSampleService` handles mixed case

---

## Tasks

- [ ] Normalize language to lowercase in `AsciidocParser.parseCodeBlocks()` (line 162-164)
- [ ] If Feature 57 is done: normalize language filter input to lowercase in `SearchService.searchCodeSamples()`
- [ ] If Feature 57 is NOT done: ensure `CodeSampleService.searchCodeSamples()` lowercases filter input
- [ ] Add unit test: `AsciidocParser.parseCodeBlocks()` with `[source,Java]` returns lowercase `"java"`
- [ ] Add unit test: `AsciidocParser.parseCodeBlocks()` with `[source,XML]` returns lowercase `"xml"`
- [ ] Add unit test: `AsciidocParser.parseCodeBlocks()` with no `[source]` returns `""`
- [ ] Add unit test: language filter with `"JAVA"` input matches stored `"java"` value
- [ ] Verify edge case: `[source,]` (empty language) stores as `""`
- [ ] Run `./gradlew test` -- all tests pass

---

## Acceptance Criteria

1. `AsciidocParser.parseCodeBlocks()` returns lowercase language values (e.g., `"java"`, not `"Java"`)
2. Code blocks without `[source]` attribute still return `""` as language (no change in this behavior)
3. Language filter with `"JAVA"` or `"Java"` input correctly matches code samples stored with `"java"` language
4. Language filter with `"java"` does NOT match code samples with `""` (empty) language
5. API responses for `/api/code-samples` show lowercase language values for newly indexed data
6. `./gradlew test` passes with zero failures
7. No false positives: unattributed code blocks are NOT returned when a specific language filter is applied

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Lowercase normalization changes API response format (breaking change for clients) | Medium | Low | Language values were already inconsistent; lowercase is more predictable; document the normalization |
| Existing cached indexes have mixed-case language until rebuild | Certain | Low | Case-insensitive comparison handles this; rebuild happens automatically on next cache refresh |
| Feature 57 dependency complicates implementation order | Medium | Low | Both orderings work; the feature spec documents both paths |
| Some `[source]` attributes use complex language identifiers (e.g., `[source,javascript]` vs `[source,js]`) | Low | Low | Out of scope; this feature only normalizes case, not language name aliases |
| Edge case: `[source,]` with empty language | Very Low | None | Regex captures empty string, trim+lowercase produces `""`, handled same as unattributed |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Normalize language in `AsciidocParser` (1 line change) | 0.1 |
| Normalize filter input in `SearchService` or `CodeSampleService` | 0.25 |
| Add/update unit tests for language normalization | 0.75 |
| Add/update unit tests for language filter edge cases | 0.5 |
| Run full test suite | 0.25 |
| **Total** | **~1.85 hours** |

---

## Files Modified

### Production Code (2 files)
- `src/main/java/com/fvd/asciidocs/parser/AsciidocParser.java` -- lowercase language in `parseCodeBlocks()` (line 163)
- `src/main/java/com/fvd/search/services/SearchService.java` -- normalize language filter input (if Feature 57 is done) OR `src/main/java/com/fvd/api/services/CodeSampleService.java` (if Feature 57 is not done)

### Test Code (estimated 2-3 files)
- `src/test/java/com/fvd/asciidocs/parser/AsciidocParserTest.java` -- test lowercase language extraction (if test file exists; create if needed)
- `src/test/java/com/fvd/search/services/SearchServiceTest.java` -- test language filter normalization
- `src/test/java/com/fvd/indexs/indexers/CodeSampleIndexerTest.java` -- verify indexed entries have lowercase language

---

END OF FILE
