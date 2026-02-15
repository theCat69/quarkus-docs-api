# Feature 69: Cache Document Parsing Results

> **Dependencies**: None. This is a self-contained performance enhancement to the document retrieval path. Compatible with all existing features.

## Summary

`GET /api/documents?path=security-overview.adoc` takes 3–7 seconds because each request reads the raw AsciiDoc file from disk via `DocStore.read()` and parses it through `DocParser.parseSections()` + `DocParser.parseCodeBlocks()`. These two parse operations iterate every line of the file, extract keywords per section (including stemming), and detect code block boundaries — all of which are deterministic for a given file. This feature adds an in-memory cache of fully-parsed `DocumentResponse` objects so that repeated requests for the same document return instantly. The cache is populated lazily on first access and invalidated when `CacheRefreshJob` updates files for a version.

## User Story

As an **AI agent consuming the Quarkus Docs API**, I want document retrieval via `GET /api/documents?path=...` to respond in under 200ms so that I can fetch multiple documents in rapid succession without timeouts or excessive latency blocking my reasoning loop.

## Motivation

### Current Behavior (Slow)

Every request to `GET /api/documents?path=security-overview.adoc` executes:

1. `DocStore.read(version, path)` — reads file from disk (~5–20ms, usually OS page-cached)
2. `DocumentTitleExtractor.extractTitle(content)` — scans first lines (~1ms)
3. `extractDescription(content)` — scans first ~20 lines (~1ms)
4. `docParser.parseSections(content)` — iterates **every line**, extracts keywords per section with stemming (~1–3s)
5. `docParser.parseCodeBlocks(content)` — iterates **every line**, detects code block boundaries (~0.5–1s)
6. DTO construction — assembles `DocumentResponse` with sections and code blocks (~1ms)

Steps 4 and 5 are the bottleneck. `parseSections()` calls `extractKeywords()` per section, which tokenizes text, filters stop words, and applies stemming. `parseCodeBlocks()` does a full line-by-line scan with regex matching. For a 1,500-line document, this takes 3–7 seconds total.

**The result is deterministic**: the same file content always produces the same `DocumentResponse`. The content only changes when `CacheRefreshJob` detects an SHA mismatch and re-fetches the file (every 6 hours at most).

### Desired Behavior (Cached)

First request: same cost as today (3–7s), but the result is cached in memory.
Subsequent requests: cache hit, response in <50ms.

After `CacheRefreshJob` runs, the cache for that version is invalidated. The next request triggers a fresh parse and caches the new result.

### Why This Matters for AI Agents

AI agents (via the MCP server) frequently perform multi-step workflows:
1. **Search**: `GET /api/documents?keywords=security&brief=true` → lightweight discovery
2. **Read**: `GET /api/documents?path=security-overview.adoc` → full document
3. **Read another**: `GET /api/documents?path=security-oidc.adoc` → full document
4. **Read section**: `GET /api/search/sections?...` → deep dive

Steps 2–3 each take 3–7 seconds today. With caching, they take <50ms on subsequent calls. The same document is often requested multiple times across agent sessions.

---

## Requirements

### R1: Add a Document Parse Cache to `DocumentService`

**File:** `src/main/java/com/fvd/api/services/DocumentService.java`

Add an in-memory cache using `ConcurrentHashMap`, following the same pattern as `SearchService.indexCache`:

```java
// Cache key: "version::path" → parsed DocumentResponse (without search-specific fields)
private final Map<String, DocumentResponse> documentCache = new ConcurrentHashMap<>();
```

**Cache key format**: `"{version}::{path}"` (e.g., `"main::security-overview.adoc"`).

The cached `DocumentResponse` contains: `title`, `description`, `path`, `subject`, `extension`, `sections`, `codeBlocks`. The `matchedKeywords` and `score` fields are **not cached** — they are search-result-specific and vary per query.

### R2: Create a Cacheable Parsed Document Record

**File:** `src/main/java/com/fvd/api/services/DocumentService.java` (private inner record) or `src/main/java/com/fvd/api/dto/ParsedDocument.java` (new file)

Create a record to hold the parse-invariant fields separately from search-specific fields:

```java
/**
 * Holds the parsed content of a document that is invariant across requests.
 * Search-specific fields (matchedKeywords, score) are not included.
 */
record ParsedDocument(
    String title,
    String description,
    String path,
    String subject,
    String extension,
    List<SectionInfo> sections,
    List<CodeBlockInfo> codeBlocks
) {}
```

This separates the cacheable data from request-specific data. A `DocumentResponse` is then assembled by combining a `ParsedDocument` with the request's `matchedKeywords` and `score`.

### R3: Lazy Cache Population in `getDocumentByPath()`

**File:** `src/main/java/com/fvd/api/services/DocumentService.java`

**Current implementation** (lines 54–65):

```java
public DocumentResponse getDocumentByPath(String version, String path) {
    Optional<String> contentOpt = docStore.read(version, path);
    if (contentOpt.isEmpty()) {
        return null;
    }
    String content = contentOpt.get();
    String extension = findExtensionForPath(version, path);
    String subject = subjectDeriver.deriveSubject(path);
    return buildDocumentResponse(path, content, extension, subject, List.of(), null);
}
```

**New implementation:**

```java
public DocumentResponse getDocumentByPath(String version, String path) {
    ParsedDocument parsed = getOrParseDocument(version, path);
    if (parsed == null) {
        return null;
    }
    return new DocumentResponse(
            parsed.title(), parsed.description(), parsed.path(),
            parsed.subject(), parsed.extension(),
            parsed.sections(), parsed.codeBlocks(),
            List.of(), null);
}

private ParsedDocument getOrParseDocument(String version, String path) {
    String cacheKey = version + "::" + path;
    ParsedDocument cached = documentCache.get(cacheKey);
    if (cached != null) {
        return cached;
    }

    Optional<String> contentOpt = docStore.read(version, path);
    if (contentOpt.isEmpty()) {
        return null;
    }

    String content = contentOpt.get();
    String extension = findExtensionForPath(version, path);
    String subject = subjectDeriver.deriveSubject(path);
    String title = DocumentTitleExtractor.extractTitle(content);
    String description = extractDescription(content);
    List<SectionInfo> sections = parseSections(content);
    List<CodeBlockInfo> codeBlocks = parseCodeBlocks(content);

    ParsedDocument parsed = new ParsedDocument(
            title, description, path, subject, extension, sections, codeBlocks);
    documentCache.put(cacheKey, parsed);
    return parsed;
}
```

### R4: Cache-Aware Search in `searchDocuments()`

**File:** `src/main/java/com/fvd/api/services/DocumentService.java`

In `searchDocuments()`, when `brief=false`, reuse the document cache instead of re-parsing:

**Current flow** (lines 94–113 in the non-brief branch):

```java
Optional<String> contentOpt = docStore.read(version, fileResult.path);
if (contentOpt.isEmpty()) {
    continue;
}
DocumentResponse doc = buildDocumentResponse(
        fileResult.path, contentOpt.get(), fileResult.extension,
        derivedSubject, matchedKws, fileResult.score);
results.add(doc);
```

**New flow:**

```java
ParsedDocument parsed = getOrParseDocument(version, fileResult.path);
if (parsed == null) {
    continue;
}
results.add(new DocumentResponse(
        parsed.title(), parsed.description(), parsed.path(),
        parsed.subject(), parsed.extension(),
        parsed.sections(), parsed.codeBlocks(),
        matchedKws, fileResult.score));
```

When `brief=true`, the existing behavior is preserved (read content for title/description only, skip parsing). The cache is **not populated** in brief mode because the full parse has not been performed.

### R5: Cache Invalidation via `invalidateDocumentCache(String version)`

**File:** `src/main/java/com/fvd/api/services/DocumentService.java`

Add a public method to invalidate all cached documents for a given version:

```java
/**
 * Invalidates the in-memory document parse cache for a specific version.
 * Should be called after documents are updated (e.g., during cache refresh).
 */
public void invalidateDocumentCache(String version) {
    String prefix = version + "::";
    documentCache.keySet().removeIf(key -> key.startsWith(prefix));
    log.info("Document parse cache invalidated for version {}", version);
}
```

### R6: Call `invalidateDocumentCache()` from `CacheRefreshJob`

**File:** `src/main/java/com/fvd/cache/jobs/CacheRefreshJob.java`

Add `DocumentService` as a dependency and call `invalidateDocumentCache()` after files are updated for a version, alongside the existing `searchService.invalidateCache(version)`:

**Current code** (line 113 in `refreshVersion()`):

```java
searchService.invalidateCache(version);
```

**New code:**

```java
searchService.invalidateCache(version);
documentService.invalidateDocumentCache(version);
```

Also in `refreshQuarkiverse()` (after line 136):

```java
searchService.invalidateCache("main");
documentService.invalidateDocumentCache("main");
```

### R7: Configuration Property for Cache Toggle (Optional)

**File:** `src/main/resources/application.properties`

Add an optional configuration property to enable/disable the document cache:

```properties
# Document parse cache (in-memory, lazy population)
app.document-cache.enabled=true
```

**File:** `src/main/java/com/fvd/api/services/DocumentService.java`

```java
@ConfigProperty(name = "app.document-cache.enabled", defaultValue = "true")
boolean documentCacheEnabled;
```

In `getOrParseDocument()`, check the flag:

```java
private ParsedDocument getOrParseDocument(String version, String path) {
    if (documentCacheEnabled) {
        String cacheKey = version + "::" + path;
        ParsedDocument cached = documentCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
    }

    // ... parse document ...

    if (documentCacheEnabled) {
        documentCache.put(cacheKey, parsed);
    }
    return parsed;
}
```

**Test profile**: enable by default (caching should be tested). Can be disabled for specific test scenarios.

```properties
%test.app.document-cache.enabled=true
```

---

## Technical Design

### Data Flow — Cache Miss (First Request)

```
DocumentResource.getDocuments(path="security-overview.adoc")
  → DocumentService.getDocumentByPath(version, path)
    → getOrParseDocument(version, path)
      → documentCache.get("main::security-overview.adoc")  // miss
      → docStore.read(version, path)                        // disk I/O
      → parseSections(content)                              // expensive parse
      → parseCodeBlocks(content)                            // expensive parse
      → documentCache.put("main::security-overview.adoc", parsed)
      → return ParsedDocument
    → assemble DocumentResponse (with matchedKeywords=[], score=null)
  → return DocumentResponse
```

### Data Flow — Cache Hit (Subsequent Request)

```
DocumentResource.getDocuments(path="security-overview.adoc")
  → DocumentService.getDocumentByPath(version, path)
    → getOrParseDocument(version, path)
      → documentCache.get("main::security-overview.adoc")  // hit!
      → return ParsedDocument                               // instant
    → assemble DocumentResponse
  → return DocumentResponse
```

### Data Flow — Cache Invalidation (Refresh Job)

```
CacheRefreshJob.refreshVersion("main")
  → fetchAndCacheDoc(...)            // re-download changed files
  → keywordIndexer.build(...)        // rebuild keyword index
  → searchService.invalidateCache("main")        // existing
  → documentService.invalidateDocumentCache("main")  // NEW
    → documentCache.keySet().removeIf("main::*")  // clear all main docs
```

### Memory Impact Analysis

| Metric | Estimate |
|--------|----------|
| Docs per version | ~300 AsciiDoc files |
| Avg parsed document size (in memory) | ~50–100 KB (sections + code blocks as Strings) |
| Max docs cached (1 version, all docs) | 300 × 100 KB = ~30 MB |
| Max docs cached (3 versions) | 3 × 30 MB = ~90 MB |
| Realistic usage (lazy, not all accessed) | ~30–50 docs per version = ~5–15 MB |

**Conclusion**: Memory impact is manageable. With lazy population, only accessed documents are cached. Even worst-case (~90 MB for 3 versions, all documents accessed) is well within typical JVM heap limits.

### Cache Strategy: Lazy vs. Eager

| Strategy | Pros | Cons |
|----------|------|------|
| **Lazy (chosen)** | Zero startup cost, only caches what's accessed, simple | First request still slow |
| Eager (warmup-time) | All requests fast from start | Adds 5–15 min to warmup, caches unused docs |
| Eager (background) | Non-blocking warmup | Complex threading, may race with refresh |

**Decision**: Lazy population. Matches the pattern used by `SearchService.getOrBuildIndex()`. The first request for each document pays the parse cost; subsequent requests are instant. This is the simplest approach and avoids unnecessary memory usage for documents that are never requested.

### Thread Safety

`ConcurrentHashMap` is used, matching the `SearchService` pattern. Key operations:
- `get()` — lock-free read
- `put()` — segment-level lock, safe for concurrent puts of the same key (last writer wins, both produce identical results since content is deterministic)
- `keySet().removeIf()` — iterates and removes atomically per entry; concurrent reads during invalidation may see stale or fresh data, which is acceptable (stale data is valid until the file on disk changes, and the refresh job has already updated the files)

No `computeIfAbsent()` is needed because the parse function may return `null` (file not found), and `ConcurrentHashMap.computeIfAbsent()` does not store `null` values. The `get()` + `put()` pattern is simpler and matches the existing codebase style.

---

## Tasks

- [ ] Create `ParsedDocument` record in `DocumentService` (private inner record) to hold cache-invariant parsed fields
- [ ] Add `ConcurrentHashMap<String, ParsedDocument> documentCache` field to `DocumentService`
- [ ] Add `@ConfigProperty(name = "app.document-cache.enabled", defaultValue = "true") boolean documentCacheEnabled` to `DocumentService`
- [ ] Implement `getOrParseDocument(String version, String path)` private method with lazy cache lookup and population
- [ ] Refactor `getDocumentByPath()` to use `getOrParseDocument()` instead of direct parsing
- [ ] Refactor `searchDocuments()` non-brief branch to use `getOrParseDocument()` instead of `buildDocumentResponse()`
- [ ] Keep `searchDocuments()` brief branch unchanged (no cache population for brief mode)
- [ ] Add `invalidateDocumentCache(String version)` public method using `keySet().removeIf()`
- [ ] Inject `DocumentService` into `CacheRefreshJob`
- [ ] Call `documentService.invalidateDocumentCache(version)` in `CacheRefreshJob.refreshVersion()` after index rebuild
- [ ] Call `documentService.invalidateDocumentCache("main")` in `CacheRefreshJob.refreshQuarkiverse()` after index rebuild
- [ ] Add `app.document-cache.enabled=true` to `application.properties`
- [ ] Add `%test.app.document-cache.enabled=true` to `application.properties`
- [ ] Add unit tests for `DocumentService`:
    - First call parses document, second call returns cached (verify `docStore.read()` called once)
    - `invalidateDocumentCache(version)` clears cache for that version only
    - Cache disabled via config: every call parses fresh
    - Cache miss when file not found returns `null` and does not cache
    - `searchDocuments()` non-brief mode uses cache
    - `searchDocuments()` brief mode does not populate cache
- [ ] Add unit tests for `CacheRefreshJob`:
    - Verify `documentService.invalidateDocumentCache(version)` is called during refresh
- [ ] Add integration test:
    - `GET /api/documents?path=security-overview.adoc` returns 200 with full content (first and second call return same data)
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. **Cache hit performance**: Second request for the same document path + version returns in <100ms (no re-parsing)
2. **Cache correctness**: Cached `DocumentResponse` is identical to freshly parsed response (title, description, sections, codeBlocks)
3. **Cache invalidation**: After `CacheRefreshJob` runs, subsequent document requests reflect updated file content
4. **Version isolation**: `invalidateDocumentCache("main")` does not affect cached documents for version `"3.27"`
5. **Search integration**: `searchDocuments()` with `brief=false` benefits from the cache (does not re-parse documents already cached)
6. **Brief mode isolation**: `searchDocuments()` with `brief=true` does not populate the parse cache (avoids unnecessary full parsing)
7. **Null handling**: Requesting a non-existent document returns `null` (not found) and does not cache a null entry
8. **Config toggle**: Setting `app.document-cache.enabled=false` disables caching; every request parses fresh
9. **Thread safety**: Concurrent requests for the same document do not cause errors or corruption
10. **Backward compatibility**: No changes to the public API surface; existing request/response contracts are preserved
11. **Existing tests**: `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Memory pressure with many versions and large documents cached | Low | Medium | Lazy population limits cache to accessed docs only; typical usage caches ~30–50 docs (~5–15 MB). Monitor with JVM metrics. Add LRU eviction in a future feature if needed. |
| Stale cache served briefly during refresh (race between invalidation and file update) | Low | Low | `CacheRefreshJob` updates files on disk first, then invalidates cache. A concurrent reader may get the old cached value, which is still valid AsciiDoc — just slightly outdated. Next request gets fresh data. |
| `ParsedDocument` holds references to large `String` content in `SectionInfo` and `CodeBlockInfo`, preventing GC | Low | Medium | These are the same strings that would be in the response anyway. If memory becomes an issue, add a max-cache-size config or TTL-based eviction in a future feature. |
| `CacheRefreshJob` gains a dependency on `DocumentService`, creating a wider dependency graph | Low | Low | This is a single `@Inject` addition, following the existing pattern of `CacheRefreshJob → SearchService`. No circular dependency risk since `DocumentService` does not depend on `CacheRefreshJob`. |
| `searchDocuments()` brief mode reads content from disk (not cached) while non-brief mode uses cache — inconsistency in title/description if file changes between calls | Very Low | Low | Both modes read from the same `DocStore` which reads from disk. The only race is if a file is updated between a brief and non-brief call, which only happens during the 6h refresh window. Invalidation covers this. |
| Adding `@ConfigProperty` to `DocumentService` changes its constructor, which may require updates to existing unit tests using Mockito | Medium | Low | Update test setup to provide the config value via `@InjectMock` or constructor argument. Keep default `true` to minimize test changes. |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create `ParsedDocument` record and add cache field | 0.5 |
| Implement `getOrParseDocument()` with cache logic | 1.0 |
| Refactor `getDocumentByPath()` and `searchDocuments()` to use cache | 1.0 |
| Add `invalidateDocumentCache()` method | 0.25 |
| Wire invalidation into `CacheRefreshJob` | 0.5 |
| Add config property and wiring | 0.25 |
| Unit tests for `DocumentService` cache behavior | 2.0 |
| Unit tests for `CacheRefreshJob` invalidation call | 0.5 |
| Integration tests | 1.0 |
| Run full test suite and fix regressions | 0.5 |
| **Total** | **~7.5 hours** |

---

## Files Modified

### Production Code (3 files)

- `src/main/java/com/fvd/api/services/DocumentService.java` — add `ParsedDocument` record, `documentCache` field, `getOrParseDocument()` method, `invalidateDocumentCache()` method, refactor `getDocumentByPath()` and `searchDocuments()` to use cache
- `src/main/java/com/fvd/cache/jobs/CacheRefreshJob.java` — inject `DocumentService`, call `invalidateDocumentCache(version)` in `refreshVersion()` and `refreshQuarkiverse()`
- `src/main/resources/application.properties` — add `app.document-cache.enabled=true`

### Unchanged Production Files

- `src/main/java/com/fvd/api/resources/DocumentResource.java` — no changes; caching is internal to the service layer
- `src/main/java/com/fvd/api/dto/DocumentResponse.java` — no changes
- `src/main/java/com/fvd/docs/stores/DocStore.java` — no changes
- `src/main/java/com/fvd/docs/parser/DocParser.java` — no changes
- `src/main/java/com/fvd/asciidocs/parser/AsciidocParser.java` — no changes
- `src/main/java/com/fvd/search/services/SearchService.java` — no changes

### Test Code (estimated 2 files)

- `src/test/java/com/fvd/api/services/DocumentServiceTest.java` — add unit tests for cache hit, cache miss, invalidation, config toggle, brief-mode isolation
- `src/test/java/com/fvd/cache/jobs/CacheRefreshJobTest.java` — add/update tests to verify `documentService.invalidateDocumentCache()` is called

---

END OF FILE
