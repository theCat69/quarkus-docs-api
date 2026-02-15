# Feature 82: Un-stem Keywords in Client-Facing Responses

> **Dependencies**: None. This is a self-contained data-quality enhancement. Compatible with Feature 74 (Response Field Selection) — `matchedKeywords` and `sharedKeywords` continue to be selectable fields, but now return human-readable original forms.

## Summary

Search results and related documents currently expose stemmed keyword forms in `matchedKeywords` and `sharedKeywords` response fields (e.g., `"quarku"`, `"configur"`, `"servic"`, `"filt"`). These stems are meaningless to AI agents and waste context-window tokens on uninterpretable data. This feature stores the original (un-stemmed) word alongside the stemmed form during indexing and maps stemmed keywords back to their original forms in all client-facing responses.

## User Story

As an **AI agent consuming the API through an MCP server**, I want `matchedKeywords` and `sharedKeywords` to contain human-readable original words (e.g., `"quarkus"`, `"configuration"`, `"service"`) so that I can understand which keywords matched without needing to reverse-engineer stemming rules, and so that I can present meaningful terms to end users.

## Motivation

### Current Behavior (Stemmed Keywords)

`GET /api/search?keywords=quarkus+configuration+service` returns:

```json
{
    "results": [
        {
            "path": "config/microprofile-config.adoc",
            "title": "Configuring Your Application",
            "score": 42.5,
            "matchedKeywords": ["quarku", "configur", "servic"],
            "snippet": "..."
        }
    ],
    "totalCount": 15,
    "returnedCount": 15
}
```

`GET /api/related?path=security-overview.adoc` returns:

```json
{
    "results": [
        {
            "path": "security-oidc.adoc",
            "title": "OpenID Connect",
            "similarityScore": 0.85,
            "sharedKeywords": ["secur", "quarku", "configur", "authent", "provid"]
        }
    ]
}
```

The stems `"quarku"`, `"configur"`, `"servic"`, `"secur"`, `"authent"` are not real words — they are artifacts of the `Stemmer` class stripping suffixes (`-us`, `-ation`, `-e`, `-ity`, `-ication`).

### Desired Behavior (Original Keywords)

`GET /api/search?keywords=quarkus+configuration+service` returns:

```json
{
    "results": [
        {
            "path": "config/microprofile-config.adoc",
            "title": "Configuring Your Application",
            "score": 42.5,
            "matchedKeywords": ["quarkus", "configuration", "service"],
            "snippet": "..."
        }
    ],
    "totalCount": 15,
    "returnedCount": 15
}
```

`GET /api/related?path=security-overview.adoc` returns:

```json
{
    "results": [
        {
            "path": "security-oidc.adoc",
            "title": "OpenID Connect",
            "similarityScore": 0.85,
            "sharedKeywords": ["security", "quarkus", "configuration", "authentication", "provider"]
        }
    ]
}
```

### Impact on AI Agents

| Field | Before (stemmed) | After (original) | Agent benefit |
|-------|-------------------|-------------------|---------------|
| `matchedKeywords` in search | `["quarku", "configur"]` | `["quarkus", "configuration"]` | Agent can confirm search relevance |
| `sharedKeywords` in related | `["secur", "authent"]` | `["security", "authentication"]` | Agent understands document relationships |
| Token efficiency | Stems require agent to guess original form | Self-explanatory terms | Fewer follow-up clarification queries |

---

## Scope / Requirements

### R1: Add `original_word` Column to `file_keywords` and `section_keywords` Tables

**File:** `src/main/java/com/fvd/indexs/stores/SqliteSchemaInitializer.java`

Add an `original_word` column to both keyword tables. This column stores the original (un-stemmed, lowercased) word that produced the stemmed `word` value.

Schema changes:

```sql
-- file_keywords: add original_word column
ALTER TABLE file_keywords ADD COLUMN original_word TEXT;

-- section_keywords: add original_word column
ALTER TABLE section_keywords ADD COLUMN original_word TEXT;
```

Since the project uses `CREATE TABLE IF NOT EXISTS`, modify the `CREATE TABLE` statements to include the new column:

```sql
CREATE TABLE IF NOT EXISTS file_keywords (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id INTEGER NOT NULL,
    word TEXT NOT NULL,
    original_word TEXT,          -- NEW: original un-stemmed form
    score INTEGER NOT NULL,
    source TEXT NOT NULL DEFAULT 'body',
    frequency INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
)

CREATE TABLE IF NOT EXISTS section_keywords (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    section_id INTEGER NOT NULL,
    word TEXT NOT NULL,
    original_word TEXT,          -- NEW: original un-stemmed form
    score INTEGER NOT NULL,
    source TEXT NOT NULL DEFAULT 'body',
    frequency INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (section_id) REFERENCES sections(id) ON DELETE CASCADE
)
```

**Migration strategy:** Since indexes are fully rebuilt during cache warmup (the `doDelete` + `doInsert` pattern in `AbstractVersionedStore.write()`), no data migration is needed. The `resetSchema()` method in `SqliteSchemaInitializer` drops and recreates all tables. For production, add the column with `ALTER TABLE ... ADD COLUMN` in the schema initializer, guarded by a column-existence check, or simply use `resetSchema()` on upgrade.

### R2: Add `originalWord` Field to `KeywordScore`

**File:** `src/main/java/com/fvd/indexs/indexers/KeywordScore.java`

Add a new `originalWord` field to store the un-stemmed form alongside the stemmed `word`:

```java
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class KeywordScore {

    public String word;            // stemmed form (used for matching)
    public String originalWord;    // original un-stemmed form (for display)
    public int score;
    public String source;
    public int frequency;

    // Backward-compatible constructors...
}
```

### R3: Capture Original Word During Indexing in `KeywordIndexer`

**File:** `src/main/java/com/fvd/indexs/indexers/KeywordIndexer.java`

The `KeywordWithSource` internal record and the `extractKeywordsWithSource` method currently store only the stemmed `word`. Enhance them to also track the original (pre-stemming) token.

**Current flow:**
1. `parser.extractKeywords(content)` returns `Map<String, Integer>` (stemmed → frequency)
2. `Stemmer.stem(token)` is called in `extractKeywords()`

**Required change:** The `AsciidocParser.extractKeywords()` currently returns only stemmed keywords. Introduce a new method or modify the indexer to capture the original token before stemming. The recommended approach is to modify `KeywordWithSource` to include an `originalWord` field and track the longest original token that produced each stem:

```java
record KeywordWithSource(String word, String originalWord, int score, String source, int frequency) {
}
```

When multiple original tokens stem to the same form (e.g., "configure", "configuration", "configuring" → "configur"), keep the **longest** original token as it is the most descriptive form.

### R4: Persist `original_word` in `KeywordIndexStore`

**File:** `src/main/java/com/fvd/indexs/stores/KeywordIndexStore.java`

Update the `doInsert` method to write `original_word`:

```java
// Current INSERT for file_keywords:
"INSERT INTO file_keywords (file_id, word, score, source, frequency) VALUES (?, ?, ?, ?, ?)"

// Updated INSERT:
"INSERT INTO file_keywords (file_id, word, original_word, score, source, frequency) VALUES (?, ?, ?, ?, ?, ?)"
```

Update the `loadFileEntries` method to read `original_word`:

```java
// In the file keywords JOIN query, add fk.original_word
// When constructing KeywordScore, pass the original_word
String originalWord = rs.getString("original_word");
entry.keywords.add(new KeywordScore(word, originalWord, score, source, frequency));
```

Apply the same changes to section keyword INSERT and SELECT queries.

### R5: Map Stemmed Keywords to Originals in `RelatedDocumentService.extractSharedKeywords()`

**File:** `src/main/java/com/fvd/api/services/RelatedDocumentService.java`

Currently, `extractSharedKeywords()` returns stemmed keyword strings from the keyword vectors. After R2, the `KeywordScore` objects contain `originalWord`. Update `buildKeywordVector()` to also build an original-word lookup map, and use it in `extractSharedKeywords()`:

```java
// Current: returns List<String> of stemmed keywords
List<String> shared = extractSharedKeywords(sourceVector, candidateVector, maxSharedKeywords);

// Updated: return original forms instead of stemmed forms
// Build a stemmed → originalWord map from source + candidate KeywordScores
// When returning shared keywords, look up the original form
```

### R6: Ensure `matchedKeywords` in Search Responses Uses Original Forms

**Files:**
- `src/main/java/com/fvd/api/services/QuickSearchService.java`
- `src/main/java/com/fvd/search/services/SearchService.java`

The `QuickSearchService` already maps `MatchedKeyword::originalKeyword` (line 66), which returns the **user's query keyword** (e.g., "quarkus" when the user searched for "quarkus"). This is correct for the search use case — `matchedKeywords` reflects what the user searched for.

For `sharedKeywords` in related documents, the keywords come from the **index** (not from user input), so they need the stored `originalWord` from the index. This is the primary gap.

**No change needed for `QuickSearchService`** — it already uses `originalKeyword` from `MatchedKeyword`.

---

## Technical Design

### Data Flow

```
Indexing (write path):
  Token "configuration"
    → Stemmer.stem("configuration") → "configur"
    → Store in SQLite: word="configur", original_word="configuration"

Search (read path - matchedKeywords):
  User query "configuration"
    → Stemmer.stem("configuration") → "configur"
    → Match against index word="configur"
    → MatchedKeyword.originalKeyword = "configuration" (from user query)
    → Response: matchedKeywords = ["configuration"]    ✅ Already works

Related docs (read path - sharedKeywords):
  Source doc keywords: [KeywordScore(word="configur", originalWord="configuration")]
  Candidate doc keywords: [KeywordScore(word="configur", originalWord="configuration")]
    → Shared: "configur" → look up originalWord → "configuration"
    → Response: sharedKeywords = ["configuration"]     ✅ Now fixed
```

### Choosing the Best Original Word

When multiple tokens stem to the same form during indexing:
- "configure" → "configur"
- "configuration" → "configur"
- "configuring" → "configur"

**Strategy:** Keep the **longest** original token. The longest form is typically the noun form ("configuration") which is most informative. This is simple, deterministic, and produces good results.

In `extractKeywordsWithSource()`, when a stemmed form is seen again with a longer original token, update the original:

```java
if (existing == null || token.length() > existing.originalWord().length()) {
    keywords.put(stemmed, new KeywordWithSource(stemmed, token, score, source, frequency));
}
```

### Impact on Index Size

The `original_word` column adds approximately 8-15 bytes per keyword row. With ~50,000 keyword rows per version (3 versions = ~150,000 rows), this adds ~1.5-2.25 MB to the SQLite database — negligible.

---

## Request/Response Examples

### Example 1: Search with un-stemmed matchedKeywords

**Request:**
```
GET /api/search?keywords=quarkus+security+configuration
```

**Response (200):**
```json
{
    "results": [
        {
            "path": "security-overview.adoc",
            "title": "Security Overview",
            "subject": "security",
            "extension": "quarkus-core",
            "score": 35.2,
            "matchedKeywords": ["quarkus", "security", "configuration"],
            "snippet": "...Quarkus provides comprehensive security features including..."
        }
    ],
    "totalCount": 12,
    "returnedCount": 12
}
```

### Example 2: Related documents with un-stemmed sharedKeywords

**Request:**
```
GET /api/related?path=security-overview.adoc&version=main
```

**Response (200):**
```json
{
    "results": [
        {
            "path": "security-oidc.adoc",
            "title": "OpenID Connect",
            "description": "Secure your applications with OIDC",
            "subject": "security",
            "extension": "quarkus-core",
            "similarityScore": 0.85,
            "sharedKeywords": ["security", "quarkus", "configuration", "authentication", "provider"]
        }
    ],
    "totalCount": 8,
    "returnedCount": 5
}
```

### Example 3: Backward compatibility — matchedKeywords still works when no originalWord stored

If the index was built before this feature (no `original_word` in DB), the system falls back to returning the stemmed form. This ensures smooth upgrades without requiring immediate re-indexing.

---

## Implementation Notes

### Backward Compatibility with Existing Indexes

The `original_word` column is nullable (`TEXT` without `NOT NULL`). When reading from SQLite, if `original_word` is NULL (old index data), fall back to the stemmed `word`:

```java
String originalWord = rs.getString("original_word");
if (originalWord == null) {
    originalWord = word; // fall back to stemmed form
}
```

This means the feature is safe to deploy before re-indexing. After the next cache warmup (or manual refresh), all keywords will have `original_word` populated.

### Thread Safety

All changes are in the indexing write path (single-threaded per version) and the read path (read-only from SQLite). No concurrency concerns.

### `extractKeywords()` Refactoring

The `AsciidocParser.extractKeywords()` method currently returns `Map<String, Integer>` (stemmed → count). To capture original words, there are two approaches:

1. **Approach A (Recommended):** Add a new method `extractKeywordsWithOriginals()` returning `Map<String, KeywordWithOriginal>` where `KeywordWithOriginal` carries both `originalWord` and `frequency`. Only the `KeywordIndexer` uses this new method.
2. **Approach B:** Modify the existing `extractKeywords()` return type — but this changes the `DocParser` interface and impacts all callers.

**Decision:** Use Approach A. The `KeywordIndexer` directly calls `parser.tokenize()` and `Stemmer.stem()` in `extractKeywordsWithSource()` for heading and filename keywords. For body keywords, move the stemming logic into the indexer so the original token is available.

### `DocParser` Interface Impact

The `DocParser.extractKeywords()` interface method returns `Map<String, Integer>`. Rather than changing this interface, add a new method to `DocParser`:

```java
default Map<String, ExtractedKeyword> extractKeywordsWithOriginals(String text) {
    // Default implementation wraps extractKeywords() without originals
    Map<String, Integer> stemmed = extractKeywords(text);
    Map<String, ExtractedKeyword> result = new HashMap<>();
    for (var entry : stemmed.entrySet()) {
        result.put(entry.getKey(), new ExtractedKeyword(entry.getKey(), entry.getKey(), entry.getValue()));
    }
    return result;
}

record ExtractedKeyword(String stemmed, String original, int frequency) {}
```

Override this in `AsciidocParser` to capture the actual original tokens.

---

## Tasks

- [ ] Add `originalWord` field to `KeywordScore` — update constructors, keep backward compatibility
- [ ] Add `original_word` column to `file_keywords` table in `SqliteSchemaInitializer.createTables()`
- [ ] Add `original_word` column to `section_keywords` table in `SqliteSchemaInitializer.createTables()`
- [ ] Update `SqliteSchemaInitializer.resetSchema()` to handle the new column on DROP/CREATE
- [ ] Update `KeywordWithSource` record in `KeywordIndexer` to include `originalWord`
- [ ] Modify `KeywordIndexer.extractKeywordsWithSource()` to capture original tokens before stemming
- [ ] Modify `KeywordIndexer.extractSectionKeywordsWithSource()` to capture original tokens
- [ ] Modify `KeywordIndexer.applyFilenameBoostWithSource()` to capture original filename tokens
- [ ] Modify `KeywordIndexer.applyHeadingBoostsWithSource()` to capture original heading tokens
- [ ] Update `KeywordIndexer.toSortedScoresWithSource()` to pass `originalWord` to `KeywordScore`
- [ ] Update `KeywordIndexStore.doInsert()` to write `original_word` for file keywords
- [ ] Update `KeywordIndexStore.doInsert()` to write `original_word` for section keywords
- [ ] Update `KeywordIndexStore.loadFileEntries()` to read `original_word` from file keywords JOIN
- [ ] Update `KeywordIndexStore.loadFileEntries()` to read `original_word` from section keywords JOIN
- [ ] Add `ExtractedKeyword` record to `DocParser` interface (or `AsciidocParser`)
- [ ] Implement `extractKeywordsWithOriginals()` in `AsciidocParser` — tokenize, keep original, then stem
- [ ] Update `RelatedDocumentService.buildKeywordVector()` to also build a stemmed→original lookup
- [ ] Update `RelatedDocumentService.extractSharedKeywords()` to return original forms
- [ ] Add unit tests for `KeywordScore` with `originalWord`
- [ ] Add unit tests for `KeywordIndexer` — verify `originalWord` is populated on `KeywordScore`
- [ ] Add unit tests for `AsciidocParser.extractKeywordsWithOriginals()` — verify original tokens retained
- [ ] Add unit test for longest-original-wins logic (e.g., "configuration" beats "configure")
- [ ] Add integration test: `GET /api/related` returns un-stemmed `sharedKeywords`
- [ ] Add integration test: `GET /api/search` returns un-stemmed `matchedKeywords`
- [ ] Verify backward compatibility — null `original_word` in DB falls back to stemmed form
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /api/search?keywords=quarkus+configuration` returns `matchedKeywords` containing `"quarkus"` and `"configuration"` — not `"quarku"` and `"configur"`
2. `GET /api/related?path=security-overview.adoc` returns `sharedKeywords` containing readable English words (e.g., `"security"`, `"quarkus"`) — not stemmed forms
3. The `file_keywords` table contains an `original_word` column populated with un-stemmed tokens
4. The `section_keywords` table contains an `original_word` column populated with un-stemmed tokens
5. When multiple tokens stem to the same form, the longest original token is stored (e.g., `"configuration"` over `"configure"`)
6. If `original_word` is NULL (pre-upgrade data), the stemmed `word` is returned as fallback — no errors
7. All keyword indexes are correctly rebuilt during cache warmup with `original_word` populated
8. `KeywordScore.originalWord` is populated when loaded from SQLite
9. All existing tests pass unchanged — stemming and scoring logic is not affected
10. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| `ALTER TABLE ADD COLUMN` fails on existing SQLite databases with data | Medium | Medium | Use `CREATE TABLE IF NOT EXISTS` with the new column; existing DBs rebuilt via `resetSchema()` or cache warmup `doDelete` + `doInsert` cycle |
| Multiple tokens stem to same form — wrong original chosen | Low | Low | Use longest-original-wins heuristic; all originals are valid English words from the source document |
| `DocParser` interface change breaks other implementations | Low | Medium | Use `default` method on the interface; only `AsciidocParser` overrides it |
| Performance impact of storing/reading extra column | Very Low | Very Low | ~15 bytes per row, negligible on SQLite; no extra queries needed |
| `extractKeywordsWithOriginals()` adds complexity to `AsciidocParser` | Medium | Low | Method is self-contained; original `extractKeywords()` remains unchanged for other callers |
| Index rebuild required for existing cached versions | Medium | Low | Cache warmup already rebuilds indexes; next startup populates `original_word` automatically |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Add `originalWord` to `KeywordScore` and update constructors | 0.5 |
| Update SQLite schema (`SqliteSchemaInitializer`) | 0.5 |
| Update `KeywordIndexer` to capture original tokens | 2.0 |
| Add `extractKeywordsWithOriginals()` to `AsciidocParser` | 1.5 |
| Update `KeywordIndexStore` INSERT and SELECT queries | 1.0 |
| Update `RelatedDocumentService` to return original forms | 1.0 |
| Unit tests for `KeywordScore`, `KeywordIndexer`, `AsciidocParser` | 2.0 |
| Integration tests for search and related endpoints | 1.5 |
| Backward compatibility testing (null `original_word`) | 0.5 |
| Run full test suite and fix regressions | 0.5 |
| **Total** | **~11.0 hours** |

---

## Files Modified

### New Production Files (0-1 files)
- `src/main/java/com/fvd/docs/parser/ExtractedKeyword.java` — record for keyword + original (may be inner record on `DocParser` instead)

### Modified Production Files (6 files)
- `src/main/java/com/fvd/indexs/indexers/KeywordScore.java` — add `originalWord` field
- `src/main/java/com/fvd/indexs/indexers/KeywordIndexer.java` — capture original tokens during indexing
- `src/main/java/com/fvd/indexs/stores/KeywordIndexStore.java` — persist and read `original_word`
- `src/main/java/com/fvd/indexs/stores/SqliteSchemaInitializer.java` — add `original_word` column to schema
- `src/main/java/com/fvd/asciidocs/parser/AsciidocParser.java` — add `extractKeywordsWithOriginals()` method
- `src/main/java/com/fvd/api/services/RelatedDocumentService.java` — return original forms in `sharedKeywords`

### New Test Files (estimated 2 files)
- `src/test/java/com/fvd/indexs/indexers/KeywordIndexerOriginalWordTest.java` — unit tests for original word capture
- `src/test/java/com/fvd/asciidocs/parser/AsciidocParserOriginalWordTest.java` — unit tests for `extractKeywordsWithOriginals()`

### Modified Test Files (estimated 1-2 files)
- `src/test/java/com/fvd/api/services/RelatedDocumentServiceTest.java` — verify `sharedKeywords` returns originals
- Integration test file for search endpoint (verify `matchedKeywords` format)

### Unchanged Files
- `src/main/java/com/fvd/common/Stemmer.java` — no changes to stemming logic
- `src/main/java/com/fvd/search/services/SearchService.java` — uses `SearchKeywords.prepareWithOriginals()` which already maps query keywords
- `src/main/java/com/fvd/search/services/SqliteSearchScorer.java` — already passes `originalKeyword` from query; unchanged
- `src/main/java/com/fvd/search/services/MatchedKeyword.java` — already has `originalKeyword` field; unchanged
- `src/main/java/com/fvd/api/services/QuickSearchService.java` — already uses `MatchedKeyword::originalKeyword`; unchanged
- `src/main/java/com/fvd/api/dto/SearchResultRef.java` — field type unchanged (`List<String>`)
- `src/main/java/com/fvd/api/dto/RelatedDocumentRef.java` — field type unchanged (`List<String>`)

---

## Dependencies

- **None** — this feature is independent and can be implemented without any other feature.
- The existing `Stemmer`, `KeywordIndexer`, `KeywordIndexStore`, and `RelatedDocumentService` provide the foundation.
- Cache warmup will automatically rebuild indexes with the new `original_word` column populated.

---

END OF FILE
