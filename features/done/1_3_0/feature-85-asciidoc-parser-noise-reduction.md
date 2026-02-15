# Feature 85: AsciiDoc Parser Noise Reduction

> **Dependencies**: None. This is a self-contained indexing-quality enhancement. Should be implemented **before** Feature 82 (Un-stem Keywords) so that noise tokens are removed before original words are stored in the index.

## Summary

The `AsciidocParser.extractKeywords()` method currently strips code blocks before tokenizing but does not strip other AsciiDoc markup syntax. This causes markup-derived tokens like `"include"`, `"adoc"`, `"xref"`, `"source"`, `"opts"`, `"subs"`, `"ifdef"`, `"endif"`, `"caution"`, `"cols"` to be indexed as keywords. These noise keywords pollute search results — searching for `"include"` returns every document with an `include::` directive rather than documents that discuss inclusion. This feature enhances the `AsciiDocCleaner` utility (used during indexing and snippet generation) to strip additional AsciiDoc markup constructs before tokenization.

## User Story

As an **AI agent consuming the API through an MCP server**, I want search results to be based on **document content** rather than **markup syntax** so that searches for keywords like `"source"`, `"include"`, or `"note"` return documents that discuss those topics, not every document that contains AsciiDoc markup directives using those words.

## Motivation

### Current Behavior (Noise Keywords Indexed)

The `AsciidocParser.stripCodeBlocks()` removes `----` delimited code blocks. However, the remaining text still contains:

```asciidoc
////
This is a comment block that should not be indexed
////

include::_includes/prerequisites.adoc[]

:sectnums:
:description: Guide to configuring security
:keywords: security, oidc

image::images/architecture.png[Architecture diagram]

[[security-overview]]
[#security-overview]
== Security Overview

TIP: Use the `quarkus-oidc` extension for OIDC support.
NOTE: This feature requires Java 21.
WARNING: Do not expose admin endpoints publicly.
IMPORTANT: Configure TLS in production.
CAUTION: This API is experimental.

[source,java]
----
@Path("/hello")
public class HelloResource { }
----

|===
| Feature | Status
| OIDC | Stable
|===

See xref:security-oidc.adoc[OIDC guide] for details.
See <<security-overview>> for the overview.

icon:lock[] Secured endpoint

[.role-name]
This paragraph has a role.

// Single-line comment
<1> First callout
<2> Second callout
```

After `stripCodeBlocks()` and tokenization, the following **noise tokens** are indexed:
- `include`, `adoc`, `prerequisites` (from `include::` directive)
- `sectnums`, `description`, `keywords` (from attribute declarations)
- `image`, `png`, `architecture` (from `image::` macro)
- `tip`, `note`, `warning`, `important`, `caution` (from admonition markers)
- `xref`, `security-oidc` (from xref syntax — the `.adoc` suffix is stripped by tokenizer)
- `icon`, `lock` (from icon macro)
- `source`, `java` (from `[source,java]` attribute — though code blocks are stripped, the attribute line remains if it appears without a following code block, or in edge cases)
- `cols`, `opts`, `subs` (from table/block attributes)
- `role` (from `[.role-name]`)

### Desired Behavior (Noise Removed)

After this feature, the parser strips all markup syntax before tokenization. Only actual document content is indexed:
- Comment blocks → removed entirely
- `include::path[]` → removed
- `:attr: value` → removed
- `{attr}` → removed
- `image::path[]` → removed
- `icon:name[]` → removed
- `[[anchor]]`, `[#anchor]` → removed
- `TIP:`, `NOTE:`, `WARNING:`, `IMPORTANT:`, `CAUTION:` → removed (the following text is kept)
- `|===` → removed
- `xref:doc.adoc[text]` → replaced with `text` (link text kept)
- `<<anchor>>` → removed
- `[.role-name]` → removed
- `// comment` → removed
- `<1>`, `<2>` → removed

### Impact on Search Quality

| Before (noise tokens indexed) | After (noise removed) |
|-------------------------------|----------------------|
| "include" matches 200+ docs (every doc with includes) | "include" matches only docs discussing inclusion |
| "source" matches 150+ docs (every doc with code samples) | "source" matches docs about source code management |
| "note" matches 180+ docs (every doc with NOTE: admonition) | "note" matches docs about musical notes or note-taking |
| "image" matches 100+ docs (every doc with images) | "image" matches docs about container images |

---

## Scope / Requirements

### R1: Enhance `AsciiDocCleaner` with Additional Stripping Patterns

**File:** `src/main/java/com/fvd/common/utils/AsciiDocCleaner.java`

The `AsciiDocCleaner` already handles comment blocks, include directives, preprocessor directives, attribute declarations, block attributes, xref, link, and inline xref. However, several patterns are missing and some existing patterns need refinement. Add the following:

#### New Patterns

```java
// Attribute references: {attr-name} → removed
private static final Pattern ATTRIBUTE_REF = Pattern.compile("\\{[\\w-]+\\}");

// Image macros: image::path/to/image.png[alt text] → removed
private static final Pattern IMAGE_MACRO = Pattern.compile("image::?[^\\[]*\\[[^\\]]*\\]");

// Icon macros: icon:name[opts] → removed
private static final Pattern ICON_MACRO = Pattern.compile("icon:[^\\[]*\\[[^\\]]*\\]");

// Block anchor IDs: [[anchor-id]] → removed
private static final Pattern BLOCK_ANCHOR = Pattern.compile("^\\[\\[[\\w-]+\\]\\]\\s*$", Pattern.MULTILINE);

// Shorthand anchor IDs: [#anchor-id] → removed (on its own line)
private static final Pattern SHORTHAND_ANCHOR = Pattern.compile("^\\[#[\\w-]+\\]\\s*$", Pattern.MULTILINE);

// Callout markers: <1>, <2>, etc. → removed
private static final Pattern CALLOUT_MARKER = Pattern.compile("<\\d+>");

// Admonition markers: NOTE:, TIP:, WARNING:, IMPORTANT:, CAUTION: at line start → removed (keep following text)
private static final Pattern ADMONITION_MARKER = Pattern.compile("^(NOTE|TIP|WARNING|IMPORTANT|CAUTION):\\s*", Pattern.MULTILINE);

// Table delimiters: |=== → removed
private static final Pattern TABLE_DELIMITER = Pattern.compile("^\\|={3,}\\s*$", Pattern.MULTILINE);

// Table cell separators: | at start of cell → removed
private static final Pattern TABLE_CELL = Pattern.compile("^\\|\\s*", Pattern.MULTILINE);

// Single-line comments: // comment → removed (not inside URLs like https://)
private static final Pattern SINGLE_LINE_COMMENT = Pattern.compile("^//(?!/)[^\\n]*$", Pattern.MULTILINE);

// Inline anchor: <<anchor>> (without link text) → removed
private static final Pattern INLINE_ANCHOR_REF = Pattern.compile("<<[^,>]+>>");

// Role attributes: [.role-name] on its own line → removed
private static final Pattern ROLE_ATTRIBUTE = Pattern.compile("^\\[\\.[\\w-]+\\]\\s*$", Pattern.MULTILINE);

// Source block attributes: [source,language], [source], [listing] → removed
// (Already partially covered by BLOCK_ATTRIBUTE, but expand to catch more variants)
private static final Pattern SOURCE_ATTRIBUTE = Pattern.compile("^\\[(?:source|listing|literal|verse|quote|sidebar|example|passthrough)(?:,[^\\]]*)?\\]\\s*$", Pattern.MULTILINE);
```

#### Updated `clean()` Method

```java
public static String clean(String text) {
    if (text == null) {
        return "";
    }

    String result = text;
    // Existing patterns
    result = COMMENT_BLOCK.matcher(result).replaceAll("");
    result = INCLUDE_DIRECTIVE.matcher(result).replaceAll("");
    result = PREPROCESSOR.matcher(result).replaceAll("");
    result = ATTRIBUTE_DECL.matcher(result).replaceAll("");
    result = BLOCK_ATTRIBUTE.matcher(result).replaceAll("");
    result = XREF.matcher(result).replaceAll("$1");
    result = LINK.matcher(result).replaceAll("$1");
    result = INLINE_XREF.matcher(result).replaceAll("$1");

    // New patterns
    result = SINGLE_LINE_COMMENT.matcher(result).replaceAll("");
    result = ATTRIBUTE_REF.matcher(result).replaceAll("");
    result = IMAGE_MACRO.matcher(result).replaceAll("");
    result = ICON_MACRO.matcher(result).replaceAll("");
    result = BLOCK_ANCHOR.matcher(result).replaceAll("");
    result = SHORTHAND_ANCHOR.matcher(result).replaceAll("");
    result = CALLOUT_MARKER.matcher(result).replaceAll("");
    result = ADMONITION_MARKER.matcher(result).replaceAll("");
    result = TABLE_DELIMITER.matcher(result).replaceAll("");
    result = TABLE_CELL.matcher(result).replaceAll(" ");
    result = INLINE_ANCHOR_REF.matcher(result).replaceAll("");
    result = ROLE_ATTRIBUTE.matcher(result).replaceAll("");
    result = SOURCE_ATTRIBUTE.matcher(result).replaceAll("");

    result = MULTI_BLANK.matcher(result).replaceAll("\n\n");
    return result.trim();
}
```

### R2: Use `AsciiDocCleaner` in `AsciidocParser.extractKeywords()` Before Tokenization

**File:** `src/main/java/com/fvd/asciidocs/parser/AsciidocParser.java`

Currently, `extractKeywords()` calls `stripCodeBlocks()` then `tokenize()`. Add a call to `AsciiDocCleaner.clean()` between stripping code blocks and tokenizing:

```java
@Override
public Map<String, Integer> extractKeywords(String text) {
    String cleaned = stripCodeBlocks(text);
    cleaned = AsciiDocCleaner.clean(cleaned);    // NEW: strip AsciiDoc markup noise
    List<String> tokens = tokenize(cleaned);
    Map<String, Integer> counts = new HashMap<>();
    for (String token : tokens) {
        if (!KeywordIndexer.WORD_INDEX_BLACK_LIST.contains(token)) {
            counts.merge(Stemmer.stem(token), 1, Integer::sum);
        }
    }
    return counts;
}
```

**Important:** The `AsciiDocCleaner.clean()` call must happen **after** `stripCodeBlocks()` because code block delimiters (`----`) are a separate concern handled by `stripCodeBlocks()`. The `AsciiDocCleaner` handles content-level markup within prose sections.

### R3: Apply Cleaning to `parseSections()` Keyword Extraction

**File:** `src/main/java/com/fvd/asciidocs/parser/AsciidocParser.java`

The `parseSections()` method calls `extractKeywords(sectionText)` for each section. Since `extractKeywords()` now includes `AsciiDocCleaner.clean()`, section keywords are automatically cleaned. No additional changes needed in `parseSections()`.

However, verify that `parseSections()` does not pass section text that includes section headers — headers should not be cleaned (they contain the section title). Currently, `parseSections()` excludes the header line from `sectionText` (it starts collecting lines after the header match), so this is safe.

### R4: Preserve Xref/Link Display Text

The existing `AsciiDocCleaner` already handles xref and link patterns correctly:
- `xref:security-oidc.adoc[OIDC guide]` → `OIDC guide` (display text preserved)
- `link:https://example.com[Example]` → `Example` (display text preserved)
- `<<security-overview,Security Overview>>` → `Security Overview` (display text preserved)

The new `INLINE_ANCHOR_REF` pattern handles the case where there is **no display text**:
- `<<security-overview>>` → removed (no meaningful text to preserve)

### R5: Handle Edge Cases

**Comment blocks inside code blocks:** The `stripCodeBlocks()` method already removes code block content. Comment blocks (`////...////`) inside code blocks would be removed by `stripCodeBlocks()` along with the surrounding code. No double-processing concern.

**Single-line comments vs URLs:** The pattern `^//(?!/)[^\\n]*$` matches lines starting with `//` but NOT `///` (which is a comment block delimiter). This avoids matching URLs like `https://example.com` because those don't start at the beginning of a line. However, be careful with lines like `// https://example.com` — this is a valid comment and should be removed.

**Admonition blocks:** AsciiDoc supports both inline admonitions (`NOTE: text`) and block admonitions (`[NOTE]\n====\ntext\n====`). The `ADMONITION_MARKER` pattern handles inline form. Block admonitions are partially handled by the existing `BLOCK_ATTRIBUTE` pattern which catches `[NOTE]`, `[TIP]`, etc. — but these need to be added:

```java
// Extend BLOCK_ATTRIBUTE to include admonition block markers
private static final Pattern BLOCK_ATTRIBUTE = Pattern.compile(
        "^\\[(?:id=|\\.|source|listing|literal|verse|quote|sidebar|example|passthrough|NOTE|TIP|WARNING|IMPORTANT|CAUTION)[^\\]]*\\]\\s*$",
        Pattern.MULTILINE);
```

**Or** keep the existing `BLOCK_ATTRIBUTE` and add a dedicated pattern:

```java
private static final Pattern ADMONITION_BLOCK = Pattern.compile("^\\[(?:NOTE|TIP|WARNING|IMPORTANT|CAUTION)\\]\\s*$", Pattern.MULTILINE);
```

---

## Technical Design

### Pattern Application Order

The order of pattern application matters to avoid partial matches:

1. **Comment blocks** first — removes large chunks, prevents other patterns from matching inside comments
2. **Include directives** — removes whole lines
3. **Preprocessor directives** (`ifdef`, `ifndef`, `endif`) — removes conditional blocks
4. **Attribute declarations** (`:key: value`) — removes metadata
5. **Block attributes** (`[source,java]`, `[.role]`) — removes block markers
6. **Source/listing attributes** — removes block type markers
7. **Admonition blocks** (`[NOTE]`) — removes block admonition markers
8. **Role attributes** (`[.role-name]`) — removes style markers
9. **Block anchors** (`[[id]]`) — removes anchor definitions
10. **Shorthand anchors** (`[#id]`) — removes anchor definitions
11. **Xref/link** (with text) — replaces with display text
12. **Inline anchor references** (`<<anchor>>`) — removes references without text
13. **Image/icon macros** — removes media references
14. **Attribute references** (`{attr}`) — removes variable references
15. **Callout markers** (`<1>`) — removes code annotation markers
16. **Admonition markers** (`NOTE:`) — removes admonition prefix (keeps following text)
17. **Table delimiters** (`|===`) — removes table structure
18. **Table cells** (`| text`) — removes cell separators
19. **Single-line comments** (`// comment`) — removes comments
20. **Multiple blank lines** — collapse to single blank line (cleanup)

### Performance

All patterns are `static final` and compiled once. The `AsciiDocCleaner.clean()` method applies ~20 regex replacements sequentially. For a typical document (~500 lines), this adds ~1-2ms per document. Since indexing runs at startup (not per-request), this is negligible.

For snippet generation (per-request), `AsciiDocCleaner.clean()` is already called on small text fragments (~200 chars), so the additional patterns add minimal overhead.

### Backward Compatibility

This feature changes **indexed keywords** — documents will have fewer (but higher-quality) keywords after re-indexing. This means:
- Search results may change after re-indexing (fewer false positives)
- Existing cached indexes remain unchanged until the next warmup/refresh
- No API contract changes — the response format is identical

---

## Request/Response Examples

### Example 1: Before — noise keyword matches

**Request:**
```
GET /api/search?keywords=include
```

**Response (200) — BEFORE feature:**
```json
{
    "results": [
        {
            "path": "security-overview.adoc",
            "title": "Security Overview",
            "score": 12.5,
            "matchedKeywords": ["includ"],
            "snippet": "...include::_includes/prerequisites.adoc[]..."
        },
        {
            "path": "getting-started.adoc",
            "title": "Getting Started",
            "score": 10.2,
            "matchedKeywords": ["includ"],
            "snippet": "...include::_includes/common-setup.adoc[]..."
        }
    ],
    "totalCount": 200,
    "returnedCount": 20
}
```

Every document with `include::` directives matches — 200+ results, mostly false positives.

### Example 2: After — meaningful matches only

**Response (200) — AFTER feature:**
```json
{
    "results": [
        {
            "path": "cdi-inclusion.adoc",
            "title": "CDI Bean Inclusion and Exclusion",
            "score": 18.7,
            "matchedKeywords": ["includ"],
            "snippet": "...You can include or exclude CDI beans from discovery..."
        }
    ],
    "totalCount": 5,
    "returnedCount": 5
}
```

Only documents that actually discuss "inclusion" as a topic match — 5 relevant results.

### Example 3: Admonition text preserved, marker removed

**Input AsciiDoc:**
```
TIP: Use the `quarkus-oidc` extension for OIDC support.
```

**After cleaning:**
```
Use the quarkus-oidc extension for OIDC support.
```

The word `"TIP"` is not indexed. The actual content (`"quarkus-oidc"`, `"extension"`, `"OIDC"`, `"support"`) is indexed.

---

## Implementation Notes

### Testing Strategy

The `AsciiDocCleaner` is a stateless utility class — unit testing is straightforward. Each new pattern should have:
1. A positive test (input containing the pattern → cleaned output)
2. A negative test (content that should NOT be removed is preserved)
3. An edge case test (pattern at document boundaries, empty content, nested patterns)

### Existing `AsciiDocCleaner` Tests

Check if `AsciiDocCleanerTest` exists. If not, create it. The existing code has no tests for `AsciiDocCleaner` — this feature is an opportunity to add comprehensive test coverage.

### `stripCodeBlocks()` vs `AsciiDocCleaner.clean()`

These two methods serve different purposes and should remain separate:
- `stripCodeBlocks()`: Removes `----` delimited code blocks (structural removal based on delimiters)
- `AsciiDocCleaner.clean()`: Removes inline/block markup from prose content (regex-based pattern matching)

The `AsciidocParser.extractKeywords()` calls both in sequence:
```java
String cleaned = stripCodeBlocks(text);       // Remove code blocks
cleaned = AsciiDocCleaner.clean(cleaned);     // Remove markup syntax
List<String> tokens = tokenize(cleaned);       // Tokenize remaining prose
```

### Impact on Section Title Extraction

Section titles are extracted by matching `^(={1,5})\\s+(.+)$`. The `AsciiDocCleaner.clean()` should NOT be applied to the raw content before `parseSections()` — it should only be applied to the content passed to `extractKeywords()`. The current code structure supports this because:
1. `parseSections()` operates on raw content (to detect section headers)
2. `extractKeywords(sectionText)` is called per-section with the section's prose content
3. `extractKeywords()` applies `AsciiDocCleaner.clean()` internally

### Expanding the `BLOCK_ATTRIBUTE` Pattern

The existing `BLOCK_ATTRIBUTE` pattern is: `^\\[(?:id=|\\.|source)[^\\]]*\\]\\s*$`

This catches `[id="..."]`, `[.role]`, and `[source,...]` but misses:
- `[listing]`, `[literal]`, `[verse]`, `[quote]`, `[sidebar]`, `[example]`
- `[NOTE]`, `[TIP]`, `[WARNING]`, `[IMPORTANT]`, `[CAUTION]`
- `[opts=...]`, `[subs=...]`, `[cols=...]`

Rather than making `BLOCK_ATTRIBUTE` overly complex, add the new dedicated patterns (`SOURCE_ATTRIBUTE`, `ADMONITION_BLOCK`, `ROLE_ATTRIBUTE`) to handle these cases separately. This is more maintainable and testable.

---

## Tasks

- [ ] Add `ATTRIBUTE_REF` pattern to `AsciiDocCleaner` — strip `{attr-name}` references
- [ ] Add `IMAGE_MACRO` pattern to `AsciiDocCleaner` — strip `image::path[alt]` macros
- [ ] Add `ICON_MACRO` pattern to `AsciiDocCleaner` — strip `icon:name[opts]` macros
- [ ] Add `BLOCK_ANCHOR` pattern to `AsciiDocCleaner` — strip `[[anchor-id]]`
- [ ] Add `SHORTHAND_ANCHOR` pattern to `AsciiDocCleaner` — strip `[#anchor-id]`
- [ ] Add `CALLOUT_MARKER` pattern to `AsciiDocCleaner` — strip `<1>`, `<2>`, etc.
- [ ] Add `ADMONITION_MARKER` pattern to `AsciiDocCleaner` — strip `NOTE:`, `TIP:`, etc. (keep text)
- [ ] Add `TABLE_DELIMITER` pattern to `AsciiDocCleaner` — strip `|===`
- [ ] Add `TABLE_CELL` pattern to `AsciiDocCleaner` — strip `| ` cell separators
- [ ] Add `SINGLE_LINE_COMMENT` pattern to `AsciiDocCleaner` — strip `// comment`
- [ ] Add `INLINE_ANCHOR_REF` pattern to `AsciiDocCleaner` — strip `<<anchor>>` (no text)
- [ ] Add `ROLE_ATTRIBUTE` pattern to `AsciiDocCleaner` — strip `[.role-name]`
- [ ] Add `SOURCE_ATTRIBUTE` pattern to `AsciiDocCleaner` — strip `[source,lang]`, `[listing]`, etc.
- [ ] Add `ADMONITION_BLOCK` pattern to `AsciiDocCleaner` — strip `[NOTE]`, `[TIP]`, etc.
- [ ] Update `AsciiDocCleaner.clean()` to apply all new patterns in correct order
- [ ] Update `AsciidocParser.extractKeywords()` to call `AsciiDocCleaner.clean()` after `stripCodeBlocks()`
- [ ] Add unit tests for each new pattern:
    - `ATTRIBUTE_REF`: `{quarkus-version}` → removed; `{` alone → preserved
    - `IMAGE_MACRO`: `image::img.png[Alt]` → removed; `image` as word → preserved
    - `ICON_MACRO`: `icon:lock[]` → removed
    - `BLOCK_ANCHOR`: `[[my-id]]` → removed; `[[` in prose → preserved
    - `SHORTHAND_ANCHOR`: `[#my-id]` → removed
    - `CALLOUT_MARKER`: `<1>` → removed; `<b>` in HTML → preserved (not digit)
    - `ADMONITION_MARKER`: `NOTE: some text` → `some text`
    - `TABLE_DELIMITER`: `|===` → removed
    - `SINGLE_LINE_COMMENT`: `// comment` → removed; `https://url` → preserved
    - `INLINE_ANCHOR_REF`: `<<anchor>>` → removed; `<<anchor,text>>` → handled by existing INLINE_XREF
    - `ROLE_ATTRIBUTE`: `[.role-name]` → removed
    - `SOURCE_ATTRIBUTE`: `[source,java]` → removed; `[source]` → removed
- [ ] Add integration test: verify noise keywords are NOT in search results after re-indexing
- [ ] Add test: verify actual content keywords ARE preserved after cleaning
- [ ] Add test: verify section titles are not affected by cleaning
- [ ] Add test: verify xref display text is preserved (regression test)
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `AsciiDocCleaner.clean()` removes comment blocks (`////...////`)
2. `AsciiDocCleaner.clean()` removes include directives (`include::path[]`)
3. `AsciiDocCleaner.clean()` removes attribute definitions (`:attr: value`) and attribute references (`{attr}`)
4. `AsciiDocCleaner.clean()` removes image macros (`image::path[alt]`)
5. `AsciiDocCleaner.clean()` removes icon macros (`icon:name[opts]`)
6. `AsciiDocCleaner.clean()` removes anchor IDs (`[[id]]`, `[#id]`)
7. `AsciiDocCleaner.clean()` removes callout markers (`<1>`, `<2>`)
8. `AsciiDocCleaner.clean()` removes admonition prefixes (`NOTE:`, `TIP:`, etc.) but preserves the following text
9. `AsciiDocCleaner.clean()` removes table delimiters (`|===`)
10. `AsciiDocCleaner.clean()` replaces xref/link syntax with display text (existing behavior preserved)
11. `AsciiDocCleaner.clean()` removes single-line comments (`// comment`)
12. `AsciiDocCleaner.clean()` removes role attributes (`[.role-name]`)
13. `AsciidocParser.extractKeywords()` calls `AsciiDocCleaner.clean()` before tokenization
14. After re-indexing, tokens like `"include"`, `"adoc"`, `"xref"`, `"source"`, `"opts"`, `"note"`, `"tip"`, `"warning"` are NOT indexed as keywords (unless they appear as actual content)
15. Actual document content (prose text, section titles, code variable names) is preserved and indexed correctly
16. All existing tests pass unchanged
17. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Overly aggressive pattern removes actual content | Medium | High | Test each pattern with both noise and legitimate uses; use `^` anchoring for line-level patterns to avoid matching mid-sentence |
| Single-line comment pattern matches URLs with `//` | Low | Medium | Pattern uses `^//(?!/)` which only matches at line start and not `///`; URLs like `https://...` are mid-line and won't match |
| Attribute reference pattern `{attr}` matches legitimate curly-brace content | Low | Low | AsciiDoc documents rarely use `{word}` outside attribute references; false positives are minimal |
| Table cell pattern removes `|` at start of content lines | Medium | Medium | Pattern matches `^\\|\\s*` — only removes `|` at line start; legitimate pipe characters mid-sentence are preserved |
| Admonition marker removal keeps text that starts with "NOTE:" in non-admonition context | Low | Low | AsciiDoc convention is `NOTE:` at line start; mid-sentence occurrences don't match due to `^` anchor |
| Regex performance with 20+ patterns on large documents | Low | Low | All patterns are compiled `static final`; AsciiDoc files average ~500 lines; total cleaning ~2ms per file |
| Search result changes after re-indexing may confuse users | Medium | Low | Changes are improvements (fewer false positives); document in release notes that search quality may change |
| Callout pattern `<\\d+>` matches HTML tags like `<1>` in non-AsciiDoc content | Very Low | Very Low | Code blocks are already stripped; callout markers only appear in AsciiDoc prose |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Add 14 new regex patterns to `AsciiDocCleaner` | 2.0 |
| Update `AsciiDocCleaner.clean()` with correct ordering | 0.5 |
| Update `AsciidocParser.extractKeywords()` to use cleaner | 0.25 |
| Unit tests for each new pattern (14 patterns × 2-3 tests each) | 3.0 |
| Integration test for noise keyword reduction | 1.0 |
| Edge case testing (URLs, nested patterns, empty content) | 1.0 |
| Run full test suite and fix regressions | 0.5 |
| **Total** | **~8.25 hours** |

---

## Files Modified

### Modified Production Files (2 files)
- `src/main/java/com/fvd/common/utils/AsciiDocCleaner.java` — add 14 new patterns and update `clean()` method
- `src/main/java/com/fvd/asciidocs/parser/AsciidocParser.java` — add `AsciiDocCleaner.clean()` call in `extractKeywords()`

### New Test Files (estimated 1-2 files)
- `src/test/java/com/fvd/common/utils/AsciiDocCleanerTest.java` — comprehensive unit tests for all patterns (new and existing)
- `src/test/java/com/fvd/asciidocs/parser/AsciidocParserNoiseReductionTest.java` — integration test verifying noise keywords are not extracted (optional — may be added to existing `AsciidocParserTest`)

### Unchanged Files
- `src/main/java/com/fvd/indexs/indexers/KeywordIndexer.java` — calls `parser.extractKeywords()` which internally uses the cleaner; no changes needed
- `src/main/java/com/fvd/search/services/SearchService.java` — no changes
- `src/main/java/com/fvd/common/Stemmer.java` — no changes
- `src/main/java/com/fvd/common/StopWords.java` — some noise words could alternatively be added to the stop word list, but stripping at the parser level is more robust because it prevents tokens from being created in the first place

---

## Dependencies

- **None** — this feature is independent and can be implemented without any other feature.
- **Recommended ordering:** Implement this feature BEFORE Feature 82 (Un-stem Keywords) so that noise tokens are removed before original words are stored in the `original_word` column. If implemented after Feature 82, a re-index is needed to clean up noise original words.

---

END OF FILE
