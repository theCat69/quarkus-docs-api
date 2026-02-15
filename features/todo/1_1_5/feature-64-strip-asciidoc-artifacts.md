# Feature 64: Strip AsciiDoc Artifacts from Descriptions and Snippets

> **Dependencies**: None. This is a utility + integration change with no structural API modifications.

## Summary

Document descriptions and search snippets include raw AsciiDoc directives that are noise for AI consumers. Common artifacts include `include::_attributes.adoc[]`, `////` comment blocks, `:categories:` metadata lines, `[id="..."]` attribute blocks, `ifdef::/endif::` preprocessor directives, and `xref:` / `link:` cross-references with markup syntax. This feature creates an `AsciiDocCleaner` utility class that strips these artifacts and integrates it into description extraction and snippet generation.

## User Story

As an **AI agent consuming document descriptions and search snippets**, I want clean text without AsciiDoc markup artifacts so that I can process and present the content without noise.

## Motivation

### Example: raw description with artifacts

A typical `extractDescription()` output from `DocumentService` (lines 142–170) might return:

```
include::_attributes.adoc[] This guide covers how to use Quarkus security with xref:security-oidc-code-flow-authentication.adoc[OIDC code flow].
```

### Example: raw snippet with artifacts

A typical `generateSnippet()` output from `QuickSearchService` (lines 102–140) or `SearchService` (lines 347–359) might return:

```
...ifdef::add-copy-button-to-env[] :add-copy-button: endif::[] //// This is a comment block //// == Getting Started...
```

### Current snippet/description generation

**`DocumentService.extractDescription()`** (lines 142–170):
- Matches `:description:` attribute or falls back to first paragraph after title
- Line 156 skips lines starting with `:` (attribute declarations) — but this only catches single-line attributes, not multi-line or `include::` directives
- Does NOT strip `include::`, `ifdef::`, `xref:`, `link:`, or comment blocks

**`QuickSearchService.generateSnippet()`** (lines 102–140):
- Finds first keyword occurrence in raw content
- Lines 120–131: Extracts ~100 chars around match with `replaceAll("\\s+", " ")` whitespace normalization
- Lines 133–139: **Fallback path** — when no keyword is found, takes first 150 chars with whitespace normalization
- Does NOT strip any AsciiDoc artifacts in either path

**`SearchService.generateSnippet()`** (lines 347–359):
- Same approach — extracts text around match offset with whitespace normalization
- Does NOT strip any AsciiDoc artifacts

**`SearchService.generateSectionSnippet()`** (lines 159–196):
- Lines 187–188: Delegates to `generateSnippet()` when keyword offset is found
- Lines 190–194: **Fallback path** — when no keyword offset is found, takes first 100 chars with whitespace normalization
- Does NOT strip any AsciiDoc artifacts in the fallback path

All four code paths would benefit from a shared cleaning utility applied after substring extraction.

---

## Requirements

### R1: Create `AsciiDocCleaner` utility class

**New file**: `src/main/java/com/fvd/common/utils/AsciiDocCleaner.java`

```java
@UtilityClass
public class AsciiDocCleaner {
    public static String clean(String text) { ... }
}
```

The `clean()` method applies the following transformations in order:

| # | Pattern | Replacement | Example |
|---|---------|-------------|---------|
| 0 | `null` input | Return `""` immediately | `null` → `""` |
| 1 | `////...////` comment blocks (multiline) | Remove entirely | `////\ncomment\n////` → `` |
| 2 | `include::...[]` directives | Remove entirely | `include::_attributes.adoc[]` → `` |
| 3 | `ifdef::...[]` / `ifndef::...[]` / `endif::[]` | Remove entirely | `ifdef::add-copy-button-to-env[]` → `` |
| 4 | Lines starting with `:` followed by word chars and `:` (attribute declarations) | Remove entire line | `:categories: web` → `` |
| 5 | `[id="..."]` and `[.something]` block attribute lines | Remove entire line | `[id="getting-started"]` → `` |
| 6 | `xref:path.adoc[link text]` | Replace with link text | `xref:security.adoc[Security Guide]` → `Security Guide` |
| 7 | `link:url[link text]` | Replace with link text | `link:https://example.com[Example]` → `Example` |
| 8 | `<<anchor,link text>>` inline xrefs | Replace with link text | `<<security,Security section>>` → `Security section` |
| 9 | Collapse multiple consecutive blank lines | Single blank line | |
| 10 | Trim leading/trailing whitespace | Standard trim | |

**Null handling**: `clean(null)` returns `""`. This is a deliberate safety choice — all call sites currently guard against null content before reaching the cleaning step, but the utility itself is defensive.

**Regex patterns**:

```java
// Comment blocks: ////...////
private static final Pattern COMMENT_BLOCK = Pattern.compile("^////.*?^////", Pattern.MULTILINE | Pattern.DOTALL);

// Include directives: include::...[]
private static final Pattern INCLUDE_DIRECTIVE = Pattern.compile("include::[^\\[]*\\[[^\\]]*\\]");

// Preprocessor directives: ifdef::...[], ifndef::...[], endif::...[]
private static final Pattern PREPROCESSOR = Pattern.compile("(?:ifdef|ifndef|endif)::[^\\[]*\\[[^\\]]*\\]");

// Attribute declarations: :key: value (full line)
private static final Pattern ATTRIBUTE_DECL = Pattern.compile("^:\\w[\\w-]*:.*$", Pattern.MULTILINE);

// Block attributes: [id="..."], [.role], [source,java]
private static final Pattern BLOCK_ATTRIBUTE = Pattern.compile("^\\[(?:id=|\\.|source)[^\\]]*\\]\\s*$", Pattern.MULTILINE);

// Xref: xref:path[text] → text
private static final Pattern XREF = Pattern.compile("xref:[^\\[]*\\[([^\\]]*)\\]");

// Link: link:url[text] → text
private static final Pattern LINK = Pattern.compile("link:[^\\[]*\\[([^\\]]*)\\]");

// Inline xref: <<anchor,text>> → text
private static final Pattern INLINE_XREF = Pattern.compile("<<[^,>]+,([^>]+)>>");

// Multiple blank lines → single blank line
private static final Pattern MULTI_BLANK = Pattern.compile("\\n{3,}");
```

### R2: Apply `AsciiDocCleaner` in `DocumentService.extractDescription()`

**Modify `DocumentService.extractDescription()`** (lines 142–170):
- After building the description string (either from `:description:` attribute or first-paragraph fallback), apply `AsciiDocCleaner.clean()` before returning

```java
private String extractDescription(String content) {
    // ... existing extraction logic ...
    return AsciiDocCleaner.clean(desc.toString());
}
```

### R3: Apply `AsciiDocCleaner` in `QuickSearchService.generateSnippet()` — keyword match path

**Modify `QuickSearchService.generateSnippet()`** (lines 120–131):
- After extracting the raw snippet substring and before applying whitespace normalization, apply `AsciiDocCleaner.clean()`

```java
// After substring extraction, before replaceAll("\\s+", " "):
String rawSnippet = content.substring(start, end);
String cleanedSnippet = AsciiDocCleaner.clean(rawSnippet);
String snippet = cleanedSnippet.replaceAll("\\s+", " ").trim();
```

> **Offset integrity note**: The `bestOffset` is computed on the **raw** content via `lowerContent.indexOf()`. This offset is used to extract the raw substring (`content.substring(start, end)`). Cleaning is applied **after** substring extraction, on the small snippet only — NOT on the full document. This means the offset calculation is unaffected by cleaning. The keyword's position within the cleaned snippet may shift relative to the raw snippet, but since the current implementation does not highlight or mark the keyword position within the snippet, this is acceptable. If keyword highlighting is ever added, it would need to re-locate the keyword within the cleaned snippet.

### R4: Apply `AsciiDocCleaner` in `SearchService.generateSnippet()`

**Modify `SearchService.generateSnippet()`** (lines 347–359):
- Same approach: apply `AsciiDocCleaner.clean()` after substring extraction, before whitespace normalization

```java
String generateSnippet(String text, int matchOffset) {
    int contextSize = searchConfig.snippet().contextSize();
    int start = Math.max(0, matchOffset - contextSize);
    int end = Math.min(text.length(), matchOffset + contextSize);
    String rawSnippet = text.substring(start, end);
    String snippet = AsciiDocCleaner.clean(rawSnippet).replaceAll("\\s+", " ").trim();
    // ... prefix/suffix handling ...
}
```

> **Offset integrity note**: Same as R3 — the `matchOffset` is computed on raw content and used to extract the raw substring. Cleaning is applied to the extracted substring, not the full document. The keyword's position within the cleaned snippet may shift, but since snippets do not highlight keyword positions, this is acceptable.

### R5: Apply `AsciiDocCleaner` to fallback snippet paths

Two snippet generation code paths operate on raw content when **no keyword offset is found** and are not covered by R3/R4. Both must also apply `AsciiDocCleaner.clean()`.

#### R5a: `SearchService.generateSectionSnippet()` fallback (lines 190–194)

When `bestOffset < 0`, the method takes the first 100 chars of raw `sectionContent`:

```java
// Current code (lines 189-195):
} else {
    int len = Math.min(100, sectionContent.length());
    result.snippet = sectionContent.substring(0, len).replaceAll("\\s+", " ").trim();
    if (sectionContent.length() > 100) {
        result.snippet = result.snippet + "...";
    }
}
```

**Change**: Apply `AsciiDocCleaner.clean()` after substring extraction, before whitespace normalization:

```java
} else {
    int len = Math.min(100, sectionContent.length());
    result.snippet = AsciiDocCleaner.clean(sectionContent.substring(0, len))
            .replaceAll("\\s+", " ").trim();
    if (sectionContent.length() > 100) {
        result.snippet = result.snippet + "...";
    }
}
```

#### R5b: `QuickSearchService.generateSnippet()` fallback (lines 133–139)

When `bestOffset < 0`, the method takes the first 150 chars of raw `content`:

```java
// Current code (lines 133-139):
// Fall back to first 150 chars
int len = Math.min(150, content.length());
String snippet = content.substring(0, len).replaceAll("\\s+", " ").trim();
if (content.length() > 150) {
    snippet = snippet + "...";
}
return snippet;
```

**Change**: Apply `AsciiDocCleaner.clean()` after substring extraction, before whitespace normalization:

```java
int len = Math.min(150, content.length());
String snippet = AsciiDocCleaner.clean(content.substring(0, len))
        .replaceAll("\\s+", " ").trim();
if (content.length() > 150) {
    snippet = snippet + "...";
}
return snippet;
```

### R6: Unit tests for `AsciiDocCleaner`

**New file**: `src/test/java/com/fvd/common/utils/AsciiDocCleanerTest.java`

Test cases:

| Test | Input | Expected Output |
|------|-------|-----------------|
| Strips include directive | `"include::_attributes.adoc[]"` | `""` |
| Strips comment block | `"before\n////\ncomment\n////\nafter"` | `"before\n\nafter"` |
| Strips attribute declaration | `":categories: web, rest"` | `""` |
| Strips ifdef/endif | `"ifdef::env[]\ntext\nendif::[]"` | `"\ntext\n"` |
| Strips block attribute id | `"[id=\"getting-started\"]"` | `""` |
| Strips block attribute role | `"[.discrete]"` | `""` |
| Extracts xref link text | `"see xref:security.adoc[Security Guide]"` | `"see Security Guide"` |
| Extracts link text | `"visit link:https://example.com[Example]"` | `"visit Example"` |
| Extracts inline xref text | `"see <<security,Security section>>"` | `"see Security section"` |
| Handles empty xref | `"xref:path.adoc[]"` | `""` |
| Preserves normal text | `"Hello world"` | `"Hello world"` |
| Handles null input | `null` | `""` |
| Handles mixed artifacts | Multi-artifact input | All artifacts stripped |
| Collapses blank lines | `"a\n\n\n\nb"` | `"a\n\nb"` |

---

## Tasks

- [ ] Create `AsciiDocCleaner` utility class in `com.fvd.common.utils` with `@UtilityClass`
- [ ] Implement `clean()` method with the regex patterns listed in R1; return `""` for null input
- [ ] Create `AsciiDocCleanerTest` with unit tests covering all patterns (R6)
- [ ] Modify `DocumentService.extractDescription()` to apply `AsciiDocCleaner.clean()` before returning (R2)
- [ ] Modify `QuickSearchService.generateSnippet()` keyword-match path to apply `AsciiDocCleaner.clean()` after substring extraction (R3)
- [ ] Modify `QuickSearchService.generateSnippet()` fallback path (lines 133–139) to apply `AsciiDocCleaner.clean()` after substring extraction (R5b)
- [ ] Modify `SearchService.generateSnippet()` to apply `AsciiDocCleaner.clean()` after substring extraction (R4)
- [ ] Modify `SearchService.generateSectionSnippet()` fallback path (lines 190–194) to apply `AsciiDocCleaner.clean()` after substring extraction (R5a)
- [ ] Verify that existing integration tests still pass — some expected assertion strings may need updating if they contained artifacts
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `AsciiDocCleaner.clean()` strips all 8 artifact types listed in R1
2. `AsciiDocCleaner.clean(null)` returns `""` (safe default — not ambiguous)
3. `AsciiDocCleaner` is annotated with `@UtilityClass` and placed in `com.fvd.common.utils`
4. `DocumentService.extractDescription()` returns cleaned descriptions (R2)
5. `QuickSearchService.generateSnippet()` returns cleaned snippets in both the keyword-match path (R3) AND the fallback path (R5b)
6. `SearchService.generateSnippet()` returns cleaned snippets (R4)
7. `SearchService.generateSectionSnippet()` fallback path returns cleaned snippets (R5a)
8. Cleaning is applied AFTER substring extraction (on the small snippet), not on the full document — offset integrity is preserved
9. Normal text (non-AsciiDoc content) passes through unchanged
10. Unit tests cover all artifact types with at least one test case each
11. All existing tests pass (integration test assertion strings updated if needed)

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Regex patterns match legitimate content (false positives) | Medium | Medium | Patterns are conservative: `include::` only matches directive syntax, attribute declarations require `:word:` format, block attributes require specific prefixes (`id=`, `.`, `source`) |
| Cleaning changes snippet offsets, causing `...` prefix/suffix to shift | Low | Low | Cleaning is applied to the extracted substring, not the full document; the offset calculation uses the raw content. Keyword highlighting (if ever added) would need to re-locate the keyword within the cleaned snippet, but current snippets do not highlight. |
| Performance impact of multiple regex passes on large content | Low | Low | Snippets are ~200 chars; descriptions are ~200 chars; regex on small strings is negligible |
| Existing integration tests assert on content that includes AsciiDoc artifacts | Medium | Medium | Update assertion strings to match cleaned output; this is a one-time change |
| New AsciiDoc syntax not covered by patterns (e.g., `image::`, `video::`) | Medium | Low | Start with the most impactful artifacts; additional patterns can be added incrementally |
| Comment blocks with unmatched `////` delimiters cause regex to consume too much | Low | Medium | The `DOTALL` flag with `^` anchor (MULTILINE) limits matching to proper block pairs; unmatched delimiters result in no match (safe fallback) |
| Fallback snippets (first N chars) start with AsciiDoc preamble artifacts | Medium | Medium | R5 ensures `AsciiDocCleaner.clean()` is applied to fallback paths, not just keyword-match paths |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create `AsciiDocCleaner` with all regex patterns | 1.5 |
| Unit tests for `AsciiDocCleaner` (~14 test cases) | 1.5 |
| Integrate into `DocumentService.extractDescription()` | 0.25 |
| Integrate into `QuickSearchService.generateSnippet()` (both paths) | 0.5 |
| Integrate into `SearchService.generateSnippet()` | 0.25 |
| Integrate into `SearchService.generateSectionSnippet()` fallback | 0.25 |
| Update existing integration test assertions (if needed) | 0.5 |
| Run tests and verify | 0.25 |
| **Total** | **~5 hours** |

---

## Files Affected

| File | Change Type | Call Sites |
|------|-------------|------------|
| NEW: `src/main/java/com/fvd/common/utils/AsciiDocCleaner.java` | Create — utility class with `clean()` method | — |
| NEW: `src/test/java/com/fvd/common/utils/AsciiDocCleanerTest.java` | Create — unit tests for all artifact patterns | — |
| `src/main/java/com/fvd/api/services/DocumentService.java` | Modify — apply `AsciiDocCleaner.clean()` in `extractDescription()` | 1 call site: `extractDescription()` (R2) |
| `src/main/java/com/fvd/api/services/QuickSearchService.java` | Modify — apply `AsciiDocCleaner.clean()` in `generateSnippet()` | 2 call sites: keyword-match path lines 120–131 (R3), fallback path lines 133–139 (R5b) |
| `src/main/java/com/fvd/search/services/SearchService.java` | Modify — apply `AsciiDocCleaner.clean()` in `generateSnippet()` and `generateSectionSnippet()` | 2 call sites: `generateSnippet()` line 351 (R4), `generateSectionSnippet()` fallback lines 190–194 (R5a) |

---

END OF FILE
