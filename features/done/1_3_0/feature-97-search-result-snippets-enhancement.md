# Feature 97: Search Result Snippets Enhancement

> **Dependencies**: None. Modifies snippet generation in `QuickSearchService` and `SearchService`. No new endpoints.

## Summary

Search result snippets currently return plain text extracted around the first keyword occurrence. This feature enhances snippets by wrapping matched keywords with `**markers**` (bold markdown) so that AI agents and downstream consumers can immediately identify which terms matched and where. The highlighting is applied in both `QuickSearchService` (file-level search via `/api/search`) and `SearchService` (section-level search). A configurable property controls whether highlighting is enabled.

## User Story

As an **MCP server processing search results**, I want matched keywords in snippets to be visually delimited with `**markers**`, so that I can programmatically identify and present the exact matched terms to the user without re-scanning the snippet text.

## Motivation

### Current Behavior

```
GET /api/search?keywords=security+authentication
→ snippet: "...Quarkus provides comprehensive security features including authentication and authorization..."
```

No indication of which words in the snippet matched the search keywords.

### Desired Behavior

```
GET /api/search?keywords=security+authentication
→ snippet: "...Quarkus provides comprehensive **security** features including **authentication** and authorization..."
```

Matched keywords are wrapped with `**` markers. Stemmed matches are also highlighted (e.g., searching "configuring" highlights "configuration").

---

## Scope / Requirements

### R1: Create a SnippetHighlighter Utility

**New file:** `src/main/java/com/fvd/search/services/SnippetHighlighter.java`

A stateless utility class that wraps matched keywords in a snippet string with `**` markers:

```java
public class SnippetHighlighter {

    /**
     * Wraps occurrences of any keyword in the snippet with ** markers.
     * Matching is case-insensitive but preserves original casing.
     * Avoids double-wrapping already-marked terms.
     */
    public static String highlight(String snippet, Collection<String> keywords) {
        if (snippet == null || snippet.isEmpty() || keywords == null || keywords.isEmpty()) {
            return snippet;
        }
        // Build regex alternation from keywords, longest first to avoid partial matches
        // Use word-boundary-aware replacement
        // Replace with **originalCaseMatch**
    }
}
```

Key rules:
- Case-insensitive matching, original case preserved in output
- Longest keywords matched first (avoid partial overlap issues, e.g., "security" before "secur")
- Stemmed forms should also be highlighted — the caller passes both original and stemmed keywords
- No double-wrapping: if a keyword is already inside `**...**`, skip it
- Word-boundary aware: don't highlight "sec" inside "section" unless "sec" is a standalone keyword

### R2: Integrate Highlighting in QuickSearchService

**File:** `src/main/java/com/fvd/api/services/QuickSearchService.java`

After generating the plain snippet in `generateSnippet()`, apply highlighting:

```java
String snippet = generateSnippet(version, fileResult.path, keywordSet);
snippet = SnippetHighlighter.highlight(snippet, keywordSet);
```

### R3: Integrate Highlighting in SearchService

**File:** `src/main/java/com/fvd/search/services/SearchService.java`

After generating the section snippet in `generateSectionSnippet()`, apply highlighting using both original and stemmed keywords:

```java
// After setting result.snippet:
Set<String> allKeywords = new HashSet<>(originalKeywords);
allKeywords.addAll(stemmedKeywords);
result.snippet = SnippetHighlighter.highlight(result.snippet, allKeywords);
```

### R4: Configuration Toggle

**File:** `src/main/resources/application.properties`

```properties
search.snippet.highlight-enabled=true
```

**File:** `src/main/java/com/fvd/search/SearchConfig.java`

Add to the `Snippet` interface:

```java
interface Snippet {
    @WithDefault("100")
    int contextSize();

    @WithDefault("true")
    boolean highlightEnabled();
}
```

Both `QuickSearchService` and `SearchService` should check `searchConfig.snippet().highlightEnabled()` before applying highlighting.

### R5: Update MetaService Description

**File:** `src/main/java/com/fvd/api/services/MetaService.java`

Update the search endpoint description to mention highlighting:

```java
"score, matchedKeywords, snippet) without full content. Snippets highlight matched " +
"keywords with **bold markers**. Best for initial discovery."
```

---

## Request/Response Examples

### Example 1: Highlighted snippet

**Request:**
```
GET /api/search?keywords=security+oidc&limit=1
```

**Response (200):**
```json
{
    "results": [
        {
            "path": "security-oidc.adoc",
            "title": "Using OpenID Connect",
            "subject": "security",
            "extension": null,
            "score": 25.5,
            "matchedKeywords": ["security", "oidc"],
            "snippet": "...Quarkus **security** integrates with **OIDC** providers for token-based authentication..."
        }
    ],
    "totalCount": 12,
    "returnedCount": 1,
    "offset": 0,
    "limit": 1,
    "hasMore": true
}
```

### Example 2: Stemmed match highlighting

**Request:**
```
GET /api/search?keywords=configuring
```

**Response (200):**
```json
{
    "results": [
        {
            "path": "config-reference.adoc",
            "title": "Configuration Reference",
            "snippet": "...guide to **configuring** your Quarkus application using **configuration** properties..."
        }
    ]
}
```

Both "configuring" (original) and "configuration" (stemmed match) are highlighted.

---

## Tasks

- [ ] Create `SnippetHighlighter` utility class with `highlight(String, Collection<String>)` method
- [ ] Handle case-insensitive matching with original case preservation
- [ ] Sort keywords by length descending to avoid partial overlap issues
- [ ] Add word-boundary awareness to prevent highlighting inside unrelated words
- [ ] Add `highlightEnabled` to `SearchConfig.Snippet` interface with default `true`
- [ ] Add `search.snippet.highlight-enabled=true` to `application.properties`
- [ ] Integrate highlighting in `QuickSearchService.generateSnippet()` result
- [ ] Inject `SearchConfig` into `QuickSearchService` (if not already injected)
- [ ] Integrate highlighting in `SearchService.generateSectionSnippet()` result
- [ ] Update `MetaService` search endpoint description to mention highlighting
- [ ] Add unit tests for `SnippetHighlighter` — basic match, case-insensitive, stemmed, no-double-wrap, word-boundary
- [ ] Add integration test asserting snippets contain `**` markers for matched keywords
- [ ] Update `TestSearchConfig` to include `highlightEnabled()` method
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /api/search?keywords=security` returns snippets with `**security**` markers around matched terms
2. Highlighting is case-insensitive: searching "Security" highlights "security" as `**security**`
3. Stemmed keyword forms are also highlighted (e.g., "configuring" → both "configuring" and "configuration" highlighted)
4. Word boundaries are respected: searching "rest" does not highlight "REST" inside "RESTEasy". Only standalone word occurrences are highlighted.
5. Each matched occurrence is wrapped exactly once, even if the same text matches multiple keywords (original + stemmed forms).
6. `search.snippet.highlight-enabled=false` disables highlighting; snippets remain plain text
7. When `search.snippet.highlight-enabled=false`, snippets are identical to pre-feature behavior (no markers present).
8. Section search snippets in `SearchService` are also highlighted
9. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Regex performance on large snippets with many keywords | Low | Low | Snippets are max ~200 chars and keywords are typically 2-5 terms. Regex is negligible. |
| Word boundary `\b` behaves unexpectedly with special characters (e.g., hyphens in "quarkus-oidc") | Medium | Medium | Test with hyphenated terms. Consider treating hyphens as word boundaries or using custom boundary logic. |
| Double-wrapping if snippet already contains `**` from AsciiDoc source | Low | Low | `AsciiDocCleaner.clean()` strips AsciiDoc formatting before snippet generation, so `**` should not be present. |
| Existing tests assert exact snippet content without `**` markers | Medium | Medium | Update affected test assertions. Tests that check `containsString("security")` still pass since `**security**` contains "security". |
| Breaking change for API consumers parsing snippets | Medium | Medium | `**` markers are valid in plain text. Consumers can strip them with `snippet.replace("**", "")`. Document in changelog. Config toggle allows disabling. |
| Stemmed keyword highlighting produces false-positive matches | Low | Low | Stemmed forms (e.g., "configur") may match related words like "configuration", "configuring", "configurator". This is by design — stemming intentionally expands recall. Acceptable trade-off for search usability. |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Implement `SnippetHighlighter` utility | 1.5 |
| Integrate into `QuickSearchService` and `SearchService` | 1.0 |
| Add config toggle and update `SearchConfig` | 0.5 |
| Unit tests for `SnippetHighlighter` | 1.5 |
| Integration tests and existing test updates | 1.5 |
| Update `MetaService` and `TestSearchConfig` | 0.5 |
| **Total** | **~6.5 hours** |

---

## Files Modified

### New Production Files (1 file)
- `src/main/java/com/fvd/search/services/SnippetHighlighter.java` — stateless utility for keyword highlighting in snippets

### Modified Production Files (5 files)
- `src/main/java/com/fvd/api/services/QuickSearchService.java` — apply highlighting after snippet generation
- `src/main/java/com/fvd/search/services/SearchService.java` — apply highlighting after section snippet generation
- `src/main/java/com/fvd/search/SearchConfig.java` — add `highlightEnabled` to `Snippet` interface
- `src/main/resources/application.properties` — add `search.snippet.highlight-enabled=true`
- `src/main/java/com/fvd/api/services/MetaService.java` — update search endpoint description

### Modified Test Files (2+ files)
- `src/test/java/com/fvd/search/services/SnippetHighlighterTest.java` — new unit test file
- `src/test/java/com/fvd/search/TestSearchConfig.java` — add `highlightEnabled()` stub
- `src/test/java/com/fvd/api/resources/ApiSearchResourceTest.java` — update snippet assertions if needed

---

END OF FILE
