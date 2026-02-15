# Feature 63: Add Keywords Field to ExtensionInfo in Catalog

> **Dependencies**: None (independent). Can be implemented before or after Feature 62. If Feature 62 is done first, the `ExtensionInfo` constructor changes must be coordinated.

## Summary

`ExtensionInfo` only has `name`, `displayName`, `description`, and `docCount` fields. An AI agent browsing the catalog cannot determine what keywords relate to each extension without querying the search endpoint for each one. `SubjectInfo` already has a `keywords` field (line 21 of `SubjectInfo.java`), but `ExtensionInfo` does not. This feature adds a `List<String> keywords` field to `ExtensionInfo` and populates it with the top N most-frequent keywords aggregated across all documents belonging to each extension from the keyword index.

## User Story

As an **AI agent browsing the catalog**, I want each extension to include its most relevant keywords so that I can decide which extensions to search without making additional API calls.

## Motivation

The current `/api/catalog` response for extensions looks like:

```json
{
  "name": "quarkus-openapi-generator",
  "displayName": "Openapi Generator",
  "description": "",
  "docCount": 3
}
```

Compare this with the `subjects` section, which includes keywords:

```json
{
  "name": "security",
  "displayName": "Security",
  "description": "Authentication, authorization, and security features",
  "docCount": 15,
  "keywords": ["security", "oidc", "jwt", "rbac", "authentication"]
}
```

An agent can use `SubjectInfo.keywords` to refine search queries, but has no equivalent for extensions. This forces the agent to make additional search calls to discover what each extension covers.

### Current data structures

**`ExtensionInfo.java`** (4 fields):
```java
public String name;
public String displayName;
public String description;
public int docCount;
```

**`SubjectInfo.java`** (5 fields — has keywords):
```java
public String name;
public String displayName;
public String description;
public int docCount;
public List<String> keywords;
```

**`FileKeywordEntry.java`** (keyword index entry per file):
```java
public String path;
public List<KeywordScore> keywords;  // keyword + score + source + frequency
public List<SectionKeywordEntry> sections;
public String extension;
```

**`KeywordScore.java`** (indexer-assigned weights per keyword):
```java
public String word;     // stemmed keyword
public int score;       // indexer-assigned relevance weight (NOT a search score)
public String source;   // "filename", "title", "section", "body"
public int frequency;   // occurrence count
```

> **Note on `KeywordScore.score`**: This is an **indexer-assigned weight** (an `int`), not a search relevance score. The indexer assigns weights based on where the keyword was found in the document structure:
> - `filename` → weight 10 (highest priority)
> - `title` (H1) → weight 8
> - `section` (H2) → weight 5
> - `subtitle` (H3+) → weight 2
> - `body` → weight 1 (base)
>
> The actual score is computed as `base_score * location_multiplier * frequency_factor` by `KeywordScorer`, then stored as an `int` in `KeywordScore.score`. These weights reflect how prominently a keyword appears in a document, making them ideal for ranking keywords per extension.

The keyword index already has all the data needed: each `FileKeywordEntry` has an `extension` field and a list of `KeywordScore` entries with indexer weights. We just need to aggregate across files by extension.

---

## Requirements

### R1: Add `List<String> keywords` field to `ExtensionInfo`

**Modify `ExtensionInfo.java`**:

```java
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionInfo {
    public String name;
    public String displayName;
    public String description;
    public int docCount;
    public List<String> keywords;
}
```

Add a backward-compatible constructor for existing callers that don't provide keywords:

```java
public ExtensionInfo(String name, String displayName, String description, int docCount) {
    this(name, displayName, description, docCount, List.of());
}
```

#### Lombok + manual constructor coexistence

This pattern is safe and well-defined in Java:

- **`@AllArgsConstructor`** generates a **5-arg constructor**: `ExtensionInfo(String, String, String, int, List<String>)` — covering all fields including the new `keywords` field.
- **`@NoArgsConstructor`** generates a **0-arg constructor**: `ExtensionInfo()`.
- The **manual 4-arg constructor** `ExtensionInfo(String, String, String, int)` is written explicitly and **delegates to the Lombok-generated 5-arg constructor** via `this(name, displayName, description, docCount, List.of())`.
- **Lombok does not prevent manual constructors.** Lombok-generated constructors and hand-written constructors coexist without conflict. Java allows any number of constructors as long as their parameter signatures differ (which they do: 0-arg, 4-arg, and 5-arg are all distinct).
- This is the same pattern already used in `KeywordScore.java`, which has `@AllArgsConstructor` (4-arg) alongside a manual 2-arg constructor that delegates to the generated one.

### R2: Compute top-N keywords per extension in `CatalogService.buildExtensionList()`

**Current code** (lines 97–125 of `CatalogService.java`):

```java
private List<ExtensionInfo> buildExtensionList(String version) {
    Optional<KeywordIndex> indexOpt = keywordIndexStore.read(version);
    if (indexOpt.isEmpty()) {
        return List.of();
    }

    KeywordIndex index = indexOpt.get();
    Map<String, Integer> extensionDocCounts = new HashMap<>();

    for (FileKeywordEntry file : index.files) {
        String ext = file.extension != null ? file.extension : "quarkus-core";
        extensionDocCounts.merge(ext, 1, Integer::sum);
    }

    List<ExtensionInfo> extensions = new ArrayList<>();
    for (Map.Entry<String, Integer> entry : extensionDocCounts.entrySet()) {
        String name = entry.getKey();
        String displayName = formatExtensionDisplayName(name);
        extensions.add(new ExtensionInfo(name, displayName, "", entry.getValue()));
    }
    // ... sort ...
}
```

**New approach**: In addition to counting docs per extension, also aggregate **indexer weights** per extension:

```
1. First pass over index.files:
   - For each file, record its extension (default "quarkus-core")
   - Count docs per extension (existing logic)
   - For each keyword in file.keywords:
     - Accumulate total indexer weight by extension + keyword word
       Map<String, Map<String, Integer>> extensionKeywordScores

2. For each extension:
   - Get the keyword-to-totalWeight map
   - Sort by total weight descending
   - Take top 15 keywords
   - Create ExtensionInfo with the keyword list
```

> **Why indexer weights are the correct metric**: `KeywordScore.score` is an `int` representing the indexer-assigned weight — a composite of where the keyword was found (filename, title, section, body) and how frequently it appears. Summing these weights across all files belonging to an extension produces a ranking that reflects how **prominently** each keyword appears across the extension's documentation. A keyword that appears in filenames and titles of multiple docs will rank higher than one that only appears in body text of a single doc. This is the desired behavior — we want to surface the keywords that best characterize the extension, not keywords that happen to match a search query.

### R3: Keyword limit configuration

The number of keywords per extension should be a reasonable constant (not configurable for now). Use `15` as the limit — this provides enough context for AI agents without bloating the response.

Define as a private constant in `CatalogService`:

```java
private static final int MAX_EXTENSION_KEYWORDS = 15;
```

### R4: Deduplicate keywords

Keywords in the index are stemmed (e.g., `"secur"` for "security"). The keywords field in `ExtensionInfo` should use the stemmed form (consistent with `SubjectInfo.keywords` which also uses stemmed keywords). No de-stemming is needed.

However, the same stemmed keyword can appear across multiple files with different indexer weights. The aggregation step (R2) sums these weights across files, so each keyword appears only once per extension in the final list.

---

## Tasks

- [ ] Add `List<String> keywords` field to `ExtensionInfo`
- [ ] Add backward-compatible 4-arg constructor to `ExtensionInfo` (defaulting keywords to `List.of()`, delegating to the Lombok-generated 5-arg constructor)
- [ ] Add `MAX_EXTENSION_KEYWORDS = 15` constant to `CatalogService`
- [ ] Modify `CatalogService.buildExtensionList()` to aggregate indexer weights per extension
- [ ] Sort aggregated keywords by total weight descending and take top 15
- [ ] Pass keywords to `ExtensionInfo` constructor
- [ ] Add unit tests for keyword aggregation:
  - Extension with multiple files → keywords aggregated and sorted by total indexer weight
  - Extension with fewer than 15 unique keywords → all keywords included
  - Extension with no keywords → empty list
  - Multiple extensions → each gets independent keyword lists
  - Keywords are deduplicated across files (same keyword from 2 files → weights summed, appears once)
- [ ] Add integration test: `/api/catalog` response extensions include `keywords` field
- [ ] Update existing tests that construct `ExtensionInfo` to use the new constructor or the backward-compatible one
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `ExtensionInfo` has a `List<String> keywords` field
2. `/api/catalog` response includes `keywords` for each extension
3. Keywords are sorted by aggregate indexer weight descending (most prominent first)
4. Maximum 15 keywords per extension
5. Keywords are stemmed (consistent with existing keyword format)
6. Extensions with no indexed keywords have `keywords: []`
7. Existing callers using the 4-arg constructor still compile (backward-compatible constructor delegates to Lombok 5-arg)
8. All existing tests pass
9. No performance regression on `/api/catalog` — keyword aggregation is done in the same index iteration pass

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Stemmed keywords are not human-readable (e.g., `"secur"` instead of `"security"`) | Medium | Medium | Consistent with `SubjectInfo.keywords`; AI agents use these for search queries where stemming is expected |
| Large extensions (e.g., `quarkus-core`) have many keywords, slowing aggregation | Low | Low | Aggregation is a single O(n) pass over the index; sorting 15 keywords is negligible |
| Adding `keywords` field increases catalog response size | Low | Low | 15 keywords × ~10 chars = ~150 bytes per extension; negligible compared to existing response |
| Lombok `@AllArgsConstructor` + manual 4-arg constructor coexistence | Low | Low | Safe — Lombok generates a 5-arg constructor, the manual 4-arg constructor has a different signature and delegates to it. Same pattern as `KeywordScore.java` which has `@AllArgsConstructor` (4-arg) + manual 2-arg constructor |
| Catalog cache (`catalogCache`) must be invalidated when indexes change | Low | Low | Already handled — `invalidateCache()` is called on index rebuild |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Modify `ExtensionInfo` — add field and backward-compatible constructor | 0.25 |
| Modify `CatalogService.buildExtensionList()` — keyword aggregation logic | 1.0 |
| Unit tests for keyword aggregation | 1.0 |
| Integration test for catalog response | 0.5 |
| Update existing tests for constructor change | 0.25 |
| Run tests and verify | 0.25 |
| **Total** | **~3.25 hours** |

---

## Files Affected

| File | Change Type |
|------|-------------|
| `src/main/java/com/fvd/api/dto/ExtensionInfo.java` | Modify — add `List<String> keywords` field and backward-compatible 4-arg constructor (coexists with Lombok 5-arg) |
| `src/main/java/com/fvd/api/services/CatalogService.java` | Modify — aggregate indexer weights per extension, pass to `ExtensionInfo` |
| Existing tests constructing `ExtensionInfo` | Modify — use backward-compatible constructor or add keywords parameter |

---

END OF FILE
