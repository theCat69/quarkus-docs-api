# Feature 77: AsciiDoc Description Cleanup

> **Dependencies**: Feature 75 (Parse & Index Document Metadata) — uses `:summary:` attribute as primary description source. Can be partially implemented without Feature 75 (the cleanup logic itself is independent), but the `:summary:` primary source requires `DocumentMetadata`.

## Summary

Document descriptions returned by the API frequently contain raw AsciiDoc markup artifacts — admonition blocks (`[WARNING]`, `[TIP]`, `[NOTE]`), attribute references (`{quarkus-version}`), `include::` directives, `ifdef`/`endif` blocks, `image::[]` macros, and unresolved `xref:[]` syntax. The current `AsciiDocCleaner.clean()` handles some patterns (comment blocks, includes, xrefs, links, attribute declarations) but misses several description-specific ones (admonition blocks, attribute references, image macros, inline formatting). Additionally, **both** `DocumentService.extractDescription()` and `RelatedDocumentService.extractDescription()` contain near-identical duplicate logic that does not use the `:summary:` attribute (present in most Quarkus docs) as the primary description source, falling back instead to fragile first-paragraph extraction. This feature extracts description logic into a shared `DescriptionExtractor` utility, uses `:summary:` as the preferred description, enhances `AsciiDocCleaner` with a `cleanDescription()` method to strip remaining markup artifacts, and truncates descriptions to 300 characters for consistent API responses.

## User Story

As an **AI agent consuming the API through an MCP server**, I want document descriptions to be clean, human-readable text without AsciiDoc markup artifacts so that I can present them to users or use them for context without spending tokens on unresolvable attribute references like `{quarkus-version}` or raw admonition blocks like `[WARNING]`.

## Motivation

### Current Behavior

The `extractDescription()` method exists in **two places** — `DocumentService` and `RelatedDocumentService` — with nearly identical logic. Both:
1. Try to match `:description:` attribute via regex
2. Fall back to first-paragraph extraction (skipping lines starting with `:` or `=`)
3. Truncate at 200 characters
4. Apply `AsciiDocCleaner.clean()` to the result

However, the first-paragraph fallback does **not** skip `include::` directives, `ifdef::` blocks, admonition markers, or block delimiters. It also does not strip `{attribute-name}` references or inline formatting.

**Example 1 — `include::` directives leak into first-paragraph fallback:**

`GET /api/documents?path=security-oidc.adoc` may return:

```json
{
    "title": "OpenID Connect (OIDC) Authorization Code Flow",
    "description": "include::_attributes.adoc[] Learn how to protect web applications using the {oidc-extension-name}. For more details, see the OIDC Bearer Token guide.",
    "path": "security-oidc.adoc"
}
```

Problems:
1. `include::_attributes.adoc[]` — the first-paragraph fallback does not skip `include::` lines (it only skips lines starting with `:` or `=`)
2. `{oidc-extension-name}` — unresolved attribute reference (not stripped by `AsciiDocCleaner.clean()`)
3. The xref is correctly resolved to display text by the existing `AsciiDocCleaner.clean()` (the xref pattern works), but the description is still polluted by the other artifacts

> **Note:** The current `AsciiDocCleaner.clean()` already strips `:attr: value` attribute declarations and `include::` directives at the content level. However, `extractDescription()` builds the description line-by-line *before* passing to `clean()`, and the first-paragraph logic includes `include::` lines because it only checks for `:` prefix, not `include::` prefix.

**Example 2 — Admonition blocks leak into description:**

```json
{
    "description": "[WARNING] ==== This guide is under development. ==== Quarkus provides several ways to configure your application."
}
```

Problems:
1. `[WARNING]` — admonition label is not stripped (neither by `extractDescription()` line-skipping nor by `AsciiDocCleaner.clean()`)
2. `====` — admonition block delimiters are not stripped
3. The actual first-paragraph content ("Quarkus provides several ways...") is buried after the admonition noise

**Example 3 — Attribute references and inline formatting:**

```json
{
    "description": "This extension allows you to configure {project-name} with *enterprise-grade* features using `quarkus-config`."
}
```

Problems:
1. `{project-name}` — unresolved attribute reference
2. `*enterprise-grade*` — raw bold markup
3. `` `quarkus-config` `` — raw monospace markup

### Desired Behavior

`GET /api/documents?path=security-oidc.adoc` returns:

```json
{
    "title": "OpenID Connect (OIDC) Authorization Code Flow",
    "description": "OIDC Authorization Code Flow mechanism for protecting web applications",
    "path": "security-oidc.adoc"
}
```

The description comes from `:summary:` (clean, no markup). When `:summary:` is absent, the first paragraph is extracted and cleaned of all AsciiDoc artifacts.

---

## Scope / Requirements

### R1: Extract Shared `DescriptionExtractor` Utility

**New File:** `src/main/java/com/fvd/common/utils/DescriptionExtractor.java`

Both `DocumentService.extractDescription()` and `RelatedDocumentService.extractDescription()` contain near-identical duplicate logic. Refactor into a shared utility class:

```java
@UtilityClass
public class DescriptionExtractor {

    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile(
        "^:description:\\s*(.+)$", Pattern.MULTILINE);
    private static final int MAX_DESCRIPTION_LENGTH = 300;

    /**
     * Extracts a clean description from document content and optional metadata.
     *
     * Priority:
     * 1. :summary: from DocumentMetadata (cleanest source)
     * 2. :description: attribute from content
     * 3. First paragraph after title (fallback)
     *
     * @param content  raw AsciiDoc content
     * @param metadata optional document metadata (may be null)
     * @return clean, truncated description
     */
    public static String extract(String content, DocumentMetadata metadata) {
        // 1. Use :summary: from metadata (cleanest source)
        if (metadata != null && metadata.hasSummary()) {
            return truncate(metadata.getSummary(), MAX_DESCRIPTION_LENGTH);
        }

        // 2. Fall back to :description: attribute
        Matcher matcher = DESCRIPTION_PATTERN.matcher(content);
        if (matcher.find()) {
            return truncate(AsciiDocCleaner.cleanDescription(matcher.group(1)),
                            MAX_DESCRIPTION_LENGTH);
        }

        // 3. Fall back to first paragraph after title
        return truncate(extractFirstParagraph(content), MAX_DESCRIPTION_LENGTH);
    }

    /**
     * Overload for callers without metadata (backward compatibility).
     */
    public static String extract(String content) {
        return extract(content, null);
    }

    // ... truncate(), extractFirstParagraph() methods (see R8, R9)
}
```

**Why a utility class:** `DescriptionExtractor` is a stateless helper with no dependencies on CDI beans. It parallels the existing `DocumentTitleExtractor` utility in the same package.

### R2: Update `DocumentService` to Use `DescriptionExtractor`

**File:** `src/main/java/com/fvd/api/services/DocumentService.java`

Remove the private `extractDescription()` method and the `DESCRIPTION_PATTERN` constant. Replace all 3 call sites with `DescriptionExtractor.extract()`:

1. **`getOrParseDocument()`** (line 228) — `extractDescription(content)` → `DescriptionExtractor.extract(content)`
2. **`getDocumentByPathBrief()`** (line 133) — `extractDescription(content)` → `DescriptionExtractor.extract(content)`
3. **`searchDocuments()`** (line 175, brief branch) — `extractDescription(contentOpt.get())` → `DescriptionExtractor.extract(contentOpt.get())`

Once Feature 75 is deployed and `DocumentMetadata` is available, these call sites should pass metadata:
```java
DescriptionExtractor.extract(content, metadata)
```

Until then, the `extract(String content)` overload gracefully falls back to `:description:` attribute and first-paragraph extraction.

### R3: Update `RelatedDocumentService` to Use `DescriptionExtractor`

**File:** `src/main/java/com/fvd/api/services/RelatedDocumentService.java`

Remove the private `extractDescription()` method (lines 193-221) and the `DESCRIPTION_PATTERN` constant (line 38). Replace the single call site in `findRelatedDocuments()` (line 127):

```java
// Before:
description = extractDescription(content);

// After:
description = DescriptionExtractor.extract(content);
```

This ensures related document descriptions receive the same cleanup as primary document descriptions — eliminating the duplicate code and the risk of the two implementations diverging.

### R4: Enhance `AsciiDocCleaner` with Description-Specific Cleanup

**File:** `src/main/java/com/fvd/common/utils/AsciiDocCleaner.java`

Add a new `cleanDescription()` method that applies all existing `clean()` patterns plus additional description-specific cleanup:

```java
/**
 * Cleans AsciiDoc markup from description text.
 * Applies all standard cleanup plus description-specific patterns:
 * admonition blocks, attribute references, image macros, etc.
 *
 * @param text the raw description text
 * @return cleaned plain text suitable for API responses
 */
public static String cleanDescription(String text) {
    if (text == null) {
        return "";
    }
    String result = clean(text); // Apply existing cleanup first
    result = stripAdmonitionLabels(result);
    result = stripAdmonitionBlocks(result);
    result = stripAttributeReferences(result);
    result = stripImageMacros(result);
    result = stripPassthroughMacros(result);
    result = stripInlineFormatting(result);
    result = stripCalloutNumbers(result);
    result = normalizeWhitespace(result);
    return result.trim();
}
```

> **Implementation ordering note (Feature 85 overlap):** Feature 85 adds many of the same patterns (attribute references, image macros, callout markers, admonition markers) directly to `AsciiDocCleaner.clean()`. If Feature 85 is implemented first, several patterns in `cleanDescription()` become redundant (harmless but unnecessary — the patterns find nothing to strip since `clean()` already removed them). If Feature 77 is implemented first, `cleanDescription()` adds these patterns only for description cleanup; Feature 85 later promotes them to `clean()`. **Recommendation:** Implement Feature 85 first, then Feature 77 only needs to add patterns NOT covered by Feature 85 (admonition block delimiters `====`, passthrough macros, inline formatting `*bold*`/`_italic_`/`` `mono` ``, whitespace normalization). Either order works correctly — the redundant patterns are idempotent.

### R5: Strip Admonition Blocks and Labels

**New patterns in `AsciiDocCleaner`:**

```java
// Admonition labels: [WARNING], [TIP], [NOTE], [IMPORTANT], [CAUTION]
private static final Pattern ADMONITION_LABEL = Pattern.compile(
    "^\\[(WARNING|TIP|NOTE|IMPORTANT|CAUTION)\\]\\s*$", Pattern.MULTILINE);

// Admonition delimiter blocks: ====...====
private static final Pattern ADMONITION_BLOCK = Pattern.compile(
    "^====+\\s*$", Pattern.MULTILINE);

// Inline admonition prefixes: WARNING: text, TIP: text, NOTE: text
private static final Pattern INLINE_ADMONITION = Pattern.compile(
    "^(WARNING|TIP|NOTE|IMPORTANT|CAUTION):\\s*", Pattern.MULTILINE);
```

Cleanup logic:
```java
private static String stripAdmonitionLabels(String text) {
    String result = ADMONITION_LABEL.matcher(text).replaceAll("");
    result = ADMONITION_BLOCK.matcher(result).replaceAll("");
    result = INLINE_ADMONITION.matcher(result).replaceAll("");
    return result;
}
```

### R6: Strip Attribute References

Replace unresolved attribute references like `{quarkus-version}`, `{oidc-extension-name}`, `{project-name}` with empty string:

```java
// Attribute references: {attribute-name}
private static final Pattern ATTRIBUTE_REF = Pattern.compile(
    "\\{[a-zA-Z][a-zA-Z0-9_-]*\\}");

private static String stripAttributeReferences(String text) {
    return ATTRIBUTE_REF.matcher(text).replaceAll("");
}
```

> **Feature 85 overlap:** Feature 85 also adds `ATTRIBUTE_REF` to `clean()`. If Feature 85 is deployed first, this pattern in `cleanDescription()` is redundant (the `clean()` call already stripped them). This is harmless — the regex finds nothing to replace.

### R7: Strip Image Macros

```java
// Image macros: image::path[alt] or image:path[alt]
private static final Pattern IMAGE_MACRO = Pattern.compile(
    "image::?[^\\[]*\\[[^\\]]*\\]");

private static String stripImageMacros(String text) {
    return IMAGE_MACRO.matcher(text).replaceAll("");
}
```

> **Feature 85 overlap:** Same as R6 — Feature 85 adds this to `clean()`.

### R8: Strip Passthrough Macros and Inline Formatting

```java
// Passthrough: pass:[content] or +content+ (inline passthrough)
private static final Pattern PASSTHROUGH_MACRO = Pattern.compile(
    "pass:\\[[^\\]]*\\]");

// Inline passthrough: +text+
private static final Pattern INLINE_PASSTHROUGH = Pattern.compile(
    "(?<=\\s|^)\\+([^+]+)\\+(?=\\s|$)");

// Bold: *text* or **text**
private static final Pattern BOLD = Pattern.compile(
    "\\*{1,2}([^*]+)\\*{1,2}");

// Italic: _text_ or __text__
private static final Pattern ITALIC = Pattern.compile(
    "_{1,2}([^_]+)_{1,2}");

// Monospace: `text`
private static final Pattern MONOSPACE = Pattern.compile(
    "`([^`]+)`");

private static String stripInlineFormatting(String text) {
    String result = PASSTHROUGH_MACRO.matcher(text).replaceAll("");
    result = INLINE_PASSTHROUGH.matcher(result).replaceAll("$1");
    result = BOLD.matcher(result).replaceAll("$1");
    result = ITALIC.matcher(result).replaceAll("$1");
    result = MONOSPACE.matcher(result).replaceAll("$1");
    return result;
}
```

> **No Feature 85 overlap:** These patterns are description-specific and NOT added by Feature 85. Stripping `*bold*`/`_italic_`/`` `mono` `` is appropriate for short API descriptions but would lose information in full document content.

### R9: Strip Callout Numbers

```java
// Callout numbers in code: <1>, <2>, etc.
private static final Pattern CALLOUT_NUMBER = Pattern.compile(
    "<\\d+>");

private static String stripCalloutNumbers(String text) {
    return CALLOUT_NUMBER.matcher(text).replaceAll("");
}
```

> **Feature 85 overlap:** Feature 85 adds `CALLOUT_MARKER` (same pattern) to `clean()`.

### R10: Normalize Whitespace and Truncate

```java
// Multiple spaces → single space
private static String normalizeWhitespace(String text) {
    return text.replaceAll("\\s+", " ").trim();
}
```

Truncation logic lives in `DescriptionExtractor`:

```java
// In DescriptionExtractor:
private static final int MAX_DESCRIPTION_LENGTH = 300;

private static String truncate(String text, int maxLength) {
    if (text == null || text.length() <= maxLength) {
        return text;
    }
    // Truncate at word boundary
    int lastSpace = text.lastIndexOf(' ', maxLength);
    if (lastSpace > maxLength * 0.7) {
        return text.substring(0, lastSpace) + "…";
    }
    return text.substring(0, maxLength) + "…";
}
```

**Truncation change justification (200→300 chars):** The current `extractDescription()` in both `DocumentService` and `RelatedDocumentService` hard-truncates at 200 characters during line accumulation (`if (desc.length() > 200) break`). This feature increases the limit to 300 characters and moves truncation to a dedicated `truncate()` method that cuts at word boundaries and appends `…`. The change from 200→300 is motivated by:
1. Analysis of `:summary:` values in Quarkus docs shows they range from 30-200 chars — 300 accommodates all summaries without truncation.
2. 300 chars is ~60-80 tokens — still compact enough for AI agent context windows.
3. The current 200-char hard cut happens mid-line during accumulation, often cutting mid-sentence. The new 300-char word-boundary truncation produces more readable descriptions.
4. This is a quality improvement, not a breaking API change — the `description` field is still a `String`.

### R11: Improve First-Paragraph Extraction

**File:** `src/main/java/com/fvd/common/utils/DescriptionExtractor.java`

The current first-paragraph fallback in both services has identical issues:
- Does not skip `include::` lines (only skips lines starting with `:`)
- Does not skip `ifdef::`, `ifndef::`, `endif::` directives
- Does not skip admonition labels (`[WARNING]`), block delimiters (`====`, `----`), or block attributes (`[source,java]`)
- Accumulates lines that may contain raw markup

Improve the extraction:

```java
private static String extractFirstParagraph(String content) {
    String[] lines = content.split("\n");
    StringBuilder desc = new StringBuilder();
    boolean foundTitle = false;
    boolean pastHeader = false;

    for (String line : lines) {
        String trimmed = line.trim();

        // Skip title line
        if (trimmed.startsWith("= ") && !foundTitle) {
            foundTitle = true;
            continue;
        }

        if (!foundTitle) continue;

        // Skip header attribute lines and directives
        if (trimmed.startsWith(":") || trimmed.startsWith("include::") ||
            trimmed.startsWith("ifdef::") || trimmed.startsWith("ifndef::") ||
            trimmed.startsWith("endif::") || trimmed.startsWith("[")) {
            continue;
        }

        // Skip blank lines before content starts
        if (!pastHeader && trimmed.isEmpty()) {
            continue;
        }

        // Stop at next section header
        if (trimmed.startsWith("==")) {
            break;
        }

        // Stop at blank line after content has started (end of first paragraph)
        if (pastHeader && trimmed.isEmpty()) {
            break;
        }

        // Skip admonition labels and block delimiters
        if (trimmed.matches("^\\[(WARNING|TIP|NOTE|IMPORTANT|CAUTION)\\]$") ||
            trimmed.matches("^={4,}$") || trimmed.matches("^-{4,}$")) {
            continue;
        }

        pastHeader = true;
        if (!desc.isEmpty()) {
            desc.append(" ");
        }
        desc.append(trimmed);
    }

    return AsciiDocCleaner.cleanDescription(desc.toString());
}
```

---

## Technical Design

### Description Source Priority

```
1. :summary: attribute (from DocumentMetadata)     → cleanest, no markup
2. :description: attribute (from content regex)     → may contain some markup
3. First paragraph after title                      → most likely to contain markup
```

Each source goes through `AsciiDocCleaner.cleanDescription()` except `:summary:` which is already clean (it's a plain text attribute defined by doc authors).

### Shared Utility Architecture

```
DescriptionExtractor (new)
├── extract(content, metadata)    → main entry point
├── extract(content)              → overload (metadata=null)
├── extractFirstParagraph()       → private fallback logic
└── truncate()                    → private word-boundary truncation

AsciiDocCleaner (enhanced)
├── clean(text)                   → existing method (unchanged)
└── cleanDescription(text)        → new method (calls clean() + extra patterns)

DocumentService
└── Uses DescriptionExtractor.extract() at 3 call sites

RelatedDocumentService
└── Uses DescriptionExtractor.extract() at 1 call site
```

This eliminates the duplicate `extractDescription()` logic and ensures all description paths receive identical cleanup.

### Two-Level Cleanup Architecture

The `AsciiDocCleaner` now has two methods:

1. **`clean(text)`** — existing method for general AsciiDoc cleanup (used by section content, full document text, keyword extraction)
2. **`cleanDescription(text)`** — new method for description-specific cleanup (calls `clean()` first, then applies additional patterns)

This separation prevents over-aggressive cleanup on full document content (e.g., stripping `*bold*` formatting from section text would lose information) while being thorough on descriptions (where formatting serves no purpose in API responses).

### Truncation Strategy

The `truncate()` method:
1. If text ≤ 300 chars, return as-is
2. Find the last space before char 300
3. If the last space is at ≥ 70% of max length (210 chars), truncate there
4. Otherwise truncate at exactly 300 chars
5. Append `…` (Unicode ellipsis, single character)

Why 300 chars (up from 200)? The current 200-char limit truncates mid-line during accumulation, often cutting mid-sentence. Analysis of `:summary:` values in Quarkus docs shows they range from 30-200 chars. First-paragraph fallbacks can be much longer. 300 chars is approximately 60-80 tokens — enough context for an AI agent without excessive token consumption. The new word-boundary truncation produces cleaner cuts than the current hard break.

### Feature 85 Pattern Overlap

Feature 85 (AsciiDoc Parser Noise Reduction) adds many of the same regex patterns to `AsciiDocCleaner.clean()` — specifically `ATTRIBUTE_REF`, `IMAGE_MACRO`, `CALLOUT_MARKER`, `ADMONITION_MARKER`, and others. The overlap is intentional and harmless:

- **If Feature 85 is implemented first:** The `clean()` call inside `cleanDescription()` already strips attribute references, image macros, callouts, and admonition markers. The description-specific patterns in `cleanDescription()` find nothing to replace (the text is already clean). The only description-specific patterns that do real work are: admonition block delimiters (`====`), passthrough macros, inline formatting (`*bold*`, `_italic_`, `` `mono` ``), and whitespace normalization.
- **If Feature 77 is implemented first:** `cleanDescription()` handles all patterns. When Feature 85 later promotes some patterns to `clean()`, the description path has redundant double-application (harmless).
- **Recommendation:** Implement Feature 85 first for maximum benefit (improves keyword indexing). Then Feature 77 adds description-specific patterns on top.

---

## Request/Response Examples

### Example 1: Description from `:summary:` attribute

**Document header:**
```asciidoc
= OpenID Connect (OIDC) Authorization Code Flow
:summary: OIDC Authorization Code Flow mechanism for protecting web applications
:categories: security,web
```

**Request:**
```
GET /api/documents?path=security-oidc-code-flow-authentication.adoc
```

**Response (after):**
```json
{
    "title": "OpenID Connect (OIDC) Authorization Code Flow",
    "description": "OIDC Authorization Code Flow mechanism for protecting web applications"
}
```

### Example 2: Cleaned first-paragraph fallback (no `:summary:`)

**Document content:**
```asciidoc
= My Custom Extension Guide

include::_attributes.adoc[]
:categories: extensions

[WARNING]
====
This feature is experimental.
====

This extension allows you to configure {project-name} with
xref:config-reference.adoc[custom configuration] for your
*enterprise* deployment. See image::diagram.png[architecture]
for details.
```

**Before cleanup (current behavior):**
```json
{
    "description": "include::_attributes.adoc[] This extension allows you to configure {project-name} with custom configuration for your *enterprise* deployment."
}
```

Note: `:categories: extensions` is correctly skipped by the current code (lines starting with `:` are skipped). But `include::_attributes.adoc[]` leaks in because it doesn't start with `:`. `{project-name}` and `*enterprise*` are not stripped by `AsciiDocCleaner.clean()`. The `[WARNING]` block is included because `[` lines aren't skipped.

**After cleanup:**
```json
{
    "description": "This extension allows you to configure with custom configuration for your enterprise deployment."
}
```

### Example 3: Long description truncated

**Document with long first paragraph (no `:summary:`):**

```json
{
    "description": "Quarkus provides comprehensive support for building reactive applications using Mutiny, Vert.x, and reactive messaging. This guide covers the reactive programming model, including reactive REST endpoints, reactive database access with Hibernate Reactive and Panache, reactive messaging with Kafka and AMQP, and the reactive…"
}
```

Note the `…` at 300 characters, truncated at word boundary (up from the previous 200-char mid-line cut).

### Example 4: Related document descriptions also cleaned

**Request:**
```
GET /api/documents/related?path=security-overview.adoc
```

**Response (after):**
```json
{
    "results": [
        {
            "path": "security-oidc.adoc",
            "title": "OpenID Connect (OIDC) Authorization Code Flow",
            "description": "OIDC Authorization Code Flow mechanism for protecting web applications",
            "similarityScore": 0.87,
            "sharedKeywords": ["security", "oidc", "authentication"]
        }
    ]
}
```

Related document descriptions now go through the same `DescriptionExtractor` as primary documents — no more dirty descriptions in related results.

---

## Implementation Notes

### Order of Regex Application

The cleanup patterns must be applied in a specific order to avoid interference:

1. `clean()` — existing patterns (comment blocks, includes, preprocessor, attributes, xref, link)
2. Admonition labels and blocks — remove `[WARNING]` and `====` delimiters
3. Attribute references — remove `{name}` after attribute declarations are already removed
4. Image macros — remove `image::[]`
5. Passthrough macros — remove `pass:[]`
6. Inline formatting — strip `*bold*`, `_italic_`, `` `monospace` ``
7. Callout numbers — remove `<1>`, `<2>`
8. Normalize whitespace — collapse multiple spaces/newlines into single space

### Backward Compatibility

- `AsciiDocCleaner.clean()` is unchanged — all existing callers continue to work
- `AsciiDocCleaner.cleanDescription()` is a new method — additive change
- `DescriptionExtractor` is a new utility class — additive change
- `DocumentService.extractDescription()` is removed (private method) — callers use `DescriptionExtractor.extract()` instead; no API impact
- `RelatedDocumentService.extractDescription()` is removed (private method) — callers use `DescriptionExtractor.extract()` instead; no API impact
- API response `description` field will contain cleaner text — this is a quality improvement, not a breaking change
- Description truncation increases from 200→300 characters — descriptions may be slightly longer than before; this is an improvement (see R10 justification)

### Existing Cleanup Already Handled by `AsciiDocCleaner.clean()`

The current `AsciiDocCleaner` already handles:
- Comment blocks (`////...////`)
- Include directives (`include::...[]`)
- Preprocessor directives (`ifdef::`, `ifndef::`, `endif::`)
- Attribute declarations (`:key: value`)
- Block attributes (`[id="..."]`, `[source,java]`)
- Xref macros (`xref:path[text]` → `text`)
- Link macros (`link:url[text]` → `text`)
- Inline xrefs (`<<anchor,text>>` → `text`)
- Multiple blank lines → single blank line

This feature ADDS (via `cleanDescription()`):
- Admonition labels and blocks (`[WARNING]`, `====`)
- Attribute references (`{name}`)
- Image macros (`image::path[alt]`)
- Passthrough macros (`pass:[content]`)
- Inline formatting (`*bold*`, `_italic_`, `` `mono` ``)
- Callout numbers (`<1>`)
- Whitespace normalization (spaces, not just blank lines)
- Truncation to 300 characters (in `DescriptionExtractor`)

### Performance

Description cleanup runs once per document retrieval (or once per cached parse). The regex patterns are compiled as static constants (same as existing `AsciiDocCleaner` patterns). The overhead is negligible — all patterns operate on a short string (typically < 500 characters).

---

## Tasks

### Shared Utility
- [ ] Create `DescriptionExtractor` utility class in `com.fvd.common.utils`
- [ ] Move `DESCRIPTION_PATTERN` constant to `DescriptionExtractor`
- [ ] Implement `extract(String content, DocumentMetadata metadata)` with 3-tier priority
- [ ] Implement `extract(String content)` overload for backward compatibility
- [ ] Implement `truncate(String text, int maxLength)` with word-boundary truncation
- [ ] Add `MAX_DESCRIPTION_LENGTH = 300` constant
- [ ] Implement improved `extractFirstParagraph(String content)` that skips `include::`, `ifdef::`, admonition labels, block delimiters

### AsciiDocCleaner Enhancement
- [ ] Add `cleanDescription(String text)` method to `AsciiDocCleaner`
- [ ] Add `ADMONITION_LABEL` regex pattern and `stripAdmonitionLabels()` method
- [ ] Add `ADMONITION_BLOCK` regex pattern (delimiter `====`)
- [ ] Add `INLINE_ADMONITION` regex pattern (`WARNING: text`)
- [ ] Add `ATTRIBUTE_REF` regex pattern and `stripAttributeReferences()` method
- [ ] Add `IMAGE_MACRO` regex pattern and `stripImageMacros()` method
- [ ] Add `PASSTHROUGH_MACRO` regex pattern and `stripPassthroughMacros()` method
- [ ] Add `BOLD`, `ITALIC`, `MONOSPACE` regex patterns and `stripInlineFormatting()` method
- [ ] Add `CALLOUT_NUMBER` regex pattern and `stripCalloutNumbers()` method
- [ ] Add `normalizeWhitespace()` method

### Service Integration
- [ ] Remove `extractDescription()` and `DESCRIPTION_PATTERN` from `DocumentService`
- [ ] Update 3 call sites in `DocumentService` to use `DescriptionExtractor.extract(content)`
- [ ] Remove `extractDescription()` and `DESCRIPTION_PATTERN` from `RelatedDocumentService`
- [ ] Update 1 call site in `RelatedDocumentService` to use `DescriptionExtractor.extract(content)`

### Tests
- [ ] Add unit tests for `AsciiDocCleaner.cleanDescription()`:
    - Admonition labels stripped: `"[WARNING]\n====\ntext\n====\nmore text"` → `"text more text"`
    - Attribute references stripped: `"Use {quarkus-version} for your project"` → `"Use for your project"`
    - Image macros stripped: `"See image::diagram.png[arch] for details"` → `"See for details"`
    - Inline formatting stripped: `"Use *bold* and _italic_ and \`mono\`"` → `"Use bold and italic and mono"`
    - Passthrough stripped: `"Use pass:[HTML] here"` → `"Use here"`
    - Callout numbers stripped: `"int x = 0; <1>"` → `"int x = 0;"`
    - Combined patterns: all patterns applied together
    - Null input → empty string
    - Clean text unchanged
- [ ] Add unit tests for `DescriptionExtractor.truncate()`:
    - Short text (< 300 chars) → unchanged
    - Long text → truncated at word boundary with `…`
    - Null → null
- [ ] Add unit tests for `DescriptionExtractor.extract(content, metadata)`:
    - With `:summary:` in metadata → uses summary
    - Without summary, with `:description:` in content → uses description attribute
    - Without summary or description → uses first paragraph
    - All sources return cleaned text
- [ ] Add unit tests for `DescriptionExtractor.extractFirstParagraph()`:
    - Skips `include::` lines
    - Skips `ifdef::` / `endif::` directives
    - Skips `[WARNING]` admonition labels
    - Skips `====` block delimiters
    - Stops at `==` section header
    - Stops at blank line after content
- [ ] Add integration test verifying clean descriptions in API response
- [ ] Add integration test verifying related document descriptions are also clean
- [ ] Verify `AsciiDocCleaner.clean()` is unchanged — existing callers unaffected
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `AsciiDocCleaner.cleanDescription("[WARNING]\n====\ntext\n====\ncontent")` returns `"text content"` (admonition stripped)
2. `AsciiDocCleaner.cleanDescription("Use {quarkus-version} for setup")` returns `"Use for setup"` (attribute reference stripped)
3. `AsciiDocCleaner.cleanDescription("See image::arch.png[diagram] here")` returns `"See here"` (image macro stripped)
4. `AsciiDocCleaner.cleanDescription("Use *bold* _italic_ \`mono\`")` returns `"Use bold italic mono"` (inline formatting stripped)
5. `AsciiDocCleaner.cleanDescription("pass:[content] here")` returns `"here"` (passthrough stripped)
6. `AsciiDocCleaner.cleanDescription("line <1> and <2>")` returns `"line and"` (callouts stripped)
7. `AsciiDocCleaner.clean()` (existing method) behavior is unchanged — no regressions
8. `DescriptionExtractor.extract(content, metadata)` uses `:summary:` from metadata as first choice when available
9. `DescriptionExtractor.extract(content, metadata)` falls back to `:description:` attribute when no summary
10. `DescriptionExtractor.extract(content, metadata)` falls back to first paragraph when no summary or description
11. Descriptions are truncated to 300 characters at word boundary with `…` suffix
12. First-paragraph extraction skips `include::`, `ifdef::`, admonition labels, and block delimiters
13. `DocumentService` no longer contains `extractDescription()` — uses `DescriptionExtractor`
14. `RelatedDocumentService` no longer contains `extractDescription()` — uses `DescriptionExtractor`
15. `GET /api/documents?path=security-oidc.adoc` returns a clean description without markup artifacts
16. `GET /api/documents/related?path=...` returns clean descriptions for related documents
17. All existing tests pass unchanged
18. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Over-aggressive cleanup: stripping `{name}` removes intentional content | Low | Medium | Only applies to `cleanDescription()`, not `clean()`; `{name}` in descriptions is almost always an unresolved attribute |
| Inline formatting regex matches partial words (e.g., `file_name` mistaken for `_italic_`) | Medium | Low | Use word-boundary-aware regex; test with real Quarkus doc descriptions |
| `DescriptionExtractor` refactor breaks existing behavior | Low | Medium | The logic is identical to the current `extractDescription()` in both services, with improvements; comprehensive tests verify parity |
| `:summary:` attribute contains markup that should be cleaned | Low | Low | Apply `cleanDescription()` even to summary values as a safety measure (though they are typically clean) |
| Truncation increase (200→300) causes unexpectedly long descriptions | Low | Low | 300 chars is still compact (~60-80 tokens); API consumers already handle variable-length strings |
| Description quality regression: some currently-good descriptions become worse | Low | Medium | Cleanup is additive (removes more artifacts); test with sample of real docs; the existing `clean()` method is unchanged |
| `ADMONITION_BLOCK` (`====`) conflicts with heading delimiters in some AsciiDoc variants | Low | Low | Standard AsciiDoc uses `====` for admonition delimiters; heading delimiters use `=` with a space; the regex requires `^====+$` on its own line |
| Feature 85 pattern overlap causes confusion about which feature "owns" a pattern | Low | Low | Document the overlap explicitly (see R4 note); patterns are idempotent regardless of implementation order |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create `DescriptionExtractor` utility class | 1.5 |
| Add `cleanDescription()` to `AsciiDocCleaner` | 1.5 |
| Add 7 new regex patterns (admonition, attribute ref, image, passthrough, formatting, callout) | 2.0 |
| Update `DocumentService` to use `DescriptionExtractor` (3 call sites) | 0.5 |
| Update `RelatedDocumentService` to use `DescriptionExtractor` (1 call site) | 0.5 |
| Improve first-paragraph extraction in `DescriptionExtractor` | 1.0 |
| Unit tests for `AsciiDocCleaner.cleanDescription()` | 2.0 |
| Unit tests for `DescriptionExtractor` (truncation, priority, first-paragraph) | 1.5 |
| Integration tests for both document and related-document descriptions | 1.0 |
| Verify existing tests pass | 0.5 |
| **Total** | **~12 hours** |

---

## Files Modified

### New Production File (1 file)
- `src/main/java/com/fvd/common/utils/DescriptionExtractor.java` — shared utility with `extract()`, `extractFirstParagraph()`, `truncate()`, `MAX_DESCRIPTION_LENGTH` constant

### Modified Production Files (3 files)
- `src/main/java/com/fvd/common/utils/AsciiDocCleaner.java` — add `cleanDescription()` method, 7 new regex patterns, `stripAdmonitionLabels()`, `stripAdmonitionBlocks()`, `stripAttributeReferences()`, `stripImageMacros()`, `stripPassthroughMacros()`, `stripInlineFormatting()`, `stripCalloutNumbers()`, `normalizeWhitespace()`
- `src/main/java/com/fvd/api/services/DocumentService.java` — remove `extractDescription()` and `DESCRIPTION_PATTERN`; replace 3 call sites with `DescriptionExtractor.extract()`
- `src/main/java/com/fvd/api/services/RelatedDocumentService.java` — remove `extractDescription()` and `DESCRIPTION_PATTERN`; replace 1 call site with `DescriptionExtractor.extract()`

### New Test Files (2 files)
- `src/test/java/com/fvd/common/utils/AsciiDocCleanerDescriptionTest.java` — unit tests for `cleanDescription()` with all new patterns
- `src/test/java/com/fvd/common/utils/DescriptionExtractorTest.java` — unit tests for `extract()` with metadata priority, truncation, first-paragraph extraction, and fallback logic

### Unchanged Files
- `src/main/java/com/fvd/common/utils/DocumentTitleExtractor.java` — title extraction is unaffected
- `src/main/java/com/fvd/asciidocs/parser/AsciidocParser.java` — parser is unaffected (description extraction is now in `DescriptionExtractor`)
- `src/main/java/com/fvd/api/dto/DocumentResponse.java` — `description` field type (`String`) is unchanged
- `src/main/java/com/fvd/api/dto/RelatedDocumentRef.java` — `description` field type (`String`) is unchanged

---

## Dependencies

- **Feature 75** (Parse & Index Document Metadata) — provides `DocumentMetadata` with `:summary:` attribute. Without Feature 75, this feature can still be fully implemented (cleanup patterns and `DescriptionExtractor` work independently), but the `:summary:` primary source requires metadata to be available. Until then, `extract(content)` (without metadata) is used.
- **Feature 85** (AsciiDoc Parser Noise Reduction) — overlapping patterns in `AsciiDocCleaner`. See R4 note for implementation ordering guidance. Either order works correctly.
- `AsciiDocCleaner.clean()` — existing method is called by `cleanDescription()` as the first step. No changes to `clean()` itself.

---

END OF FILE
