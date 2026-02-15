# Feature 76B: MetadataAwareSubjectResolver & Service Integration

> **Dependencies**: Feature 75 (Parse & Index Document Metadata) AND Feature 76A (Metadata Classification Logic in SubjectDeriver). Requires `DocumentMetadata` model, `DocumentMetadataStore`, and the metadata-aware `deriveSubject(filePath, metadata)` overload in `SubjectDeriver` to be available.

## Summary

This feature creates a `MetadataAwareSubjectResolver` service that centralizes metadata loading and subject derivation, then integrates it into all 6 service files that currently call `subjectDeriver.deriveSubject(path)`. After this feature, every API endpoint that returns or filters by subject uses metadata-driven classification instead of path-regex, causing `GET /api/catalog` to show meaningful per-subject document counts instead of 2,548 documents under "misc". The classification logic itself (mapping tables, algorithms) is implemented in Feature 76A — this feature handles the resolver service and all caller-site integration.

## User Story

As an **AI agent consuming the API through an MCP server**, I want all API endpoints — catalog, search, document retrieval, related docs, code samples, and quick search — to use metadata-based subject classification so that when I filter by `subject=security` I get all 40+ security-related docs instead of just the 6 whose filenames happen to contain "security", and when I browse the catalog I see meaningful document counts per subject instead of 2,548 docs under "misc".

## Motivation

### Current Behavior

Even after Feature 76A adds metadata classification logic to `SubjectDeriver`, all 6 service files still call the old `subjectDeriver.deriveSubject(path)` single-arg method (path-regex only). The API continues to show:

```json
{
    "subjects": [
        { "name": "misc", "displayName": "Miscellaneous", "docCount": 2548 },
        { "name": "security", "displayName": "Security", "docCount": 23 },
        { "name": "rest-apis", "displayName": "REST APIs", "docCount": 6 }
    ]
}
```

### Desired Behavior

After this feature, all callers use `MetadataAwareSubjectResolver` which loads metadata and passes it to the new `deriveSubject(filePath, metadata)` overload from Feature 76A:

```json
{
    "subjects": [
        { "name": "misc", "displayName": "Miscellaneous", "docCount": 42 },
        { "name": "security", "displayName": "Security", "docCount": 48 },
        { "name": "rest-apis", "displayName": "REST APIs", "docCount": 35 },
        { "name": "core-concepts", "displayName": "Core Concepts", "docCount": 45 },
        { "name": "data-persistence", "displayName": "Data & Persistence", "docCount": 28 },
        { "name": "cloud", "displayName": "Cloud & Containers", "docCount": 18 },
        { "name": "observability", "displayName": "Observability", "docCount": 15 },
        { "name": "getting-started", "displayName": "Getting Started", "docCount": 22 },
        { "name": "messaging", "displayName": "Messaging", "docCount": 12 },
        { "name": "testing", "displayName": "Testing", "docCount": 8 },
        { "name": "tooling", "displayName": "Tooling", "docCount": 10 },
        { "name": "extensions", "displayName": "Extensions", "docCount": 15 }
    ]
}
```

The "misc" count drops from 2,548 to ~42 (only quarkiverse docs without metadata that don't match any path regex).

---

## Scope / Requirements

### R7: Introduce `MetadataAwareSubjectResolver` for Cross-Service Metadata Access

All 6 services that call `deriveSubject()` need access to `DocumentMetadata`. Rather than having each service independently inject `DocumentMetadataStore` and manage batch loading, introduce a thin resolver service that encapsulates metadata loading and subject derivation.

**File:** `src/main/java/com/fvd/subject/services/MetadataAwareSubjectResolver.java` (NEW)

```java
/**
 * Centralizes metadata-aware subject derivation for use by all services.
 * Encapsulates the loading of DocumentMetadata and delegation to SubjectDeriver,
 * ensuring consistent classification across the entire API surface.
 *
 * Services should use this resolver instead of calling SubjectDeriver.deriveSubject() directly.
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class MetadataAwareSubjectResolver {

    private final SubjectDeriver subjectDeriver;
    private final DocumentMetadataStore documentMetadataStore;

    /**
     * Derive subject for a single document path using metadata.
     * Loads metadata lazily from the store.
     */
    public String resolveSubject(String version, String filePath) {
        DocumentMetadata metadata = documentMetadataStore
                .readByPath(version, filePath).orElse(null);
        return subjectDeriver.deriveSubject(filePath, metadata);
    }

    /**
     * Load all metadata for a version, for use in batch operations.
     * Callers should load this once and pass it to resolveSubject(filePath, metadataMap).
     */
    public Map<String, DocumentMetadata> loadMetadataMap(String version) {
        return documentMetadataStore.readAll(version);
    }

    /**
     * Derive subject for a single document path using a pre-loaded metadata map.
     * Use this in loops to avoid repeated database queries.
     */
    public String resolveSubject(String filePath, Map<String, DocumentMetadata> metadataMap) {
        DocumentMetadata metadata = metadataMap.get(filePath);
        return subjectDeriver.deriveSubject(filePath, metadata);
    }
}
```

### R5: Integrate Metadata into ALL `deriveSubject()` Callers

> **IMPORTANT:** `subjectDeriver.deriveSubject(path)` is called in **9 locations across 6 service files**. ALL callers must be updated to use `MetadataAwareSubjectResolver` to ensure consistent classification across the entire API surface.

> **Note:** The mapping tables and `deriveSubject(filePath, metadata)` overload are defined in Feature 76A. See Feature 76A for the `CATEGORY_TO_SUBJECT` mapping, `TOPIC_KEYWORDS_TO_SUBJECT` mapping, and classification algorithm details.

#### Complete Caller Inventory

| # | File | Method | Line | Context | Loading Strategy |
|---|------|--------|------|---------|-----------------|
| 1 | `DocumentService.java` | `getOrParseDocument()` | 226 | Single document parse + cache | Lazy per-document |
| 2 | `DocumentService.java` | `getDocumentByPathBrief()` | 135 | Single brief document response | Lazy per-document |
| 3 | `DocumentService.java` | `searchDocuments()` | 162 | Loop over search results | Eager batch |
| 4 | `SearchService.java` | `getFileResults()` | 79 | Loop over keyword index for subject filtering | Eager batch |
| 5 | `SearchService.java` | `searchCodeSamples()` | 312 | Loop over code sample index for subject filtering | Eager batch |
| 6 | `RelatedDocumentService.java` | `findRelatedDocuments()` | 90 | Loop over keyword index for subject filtering + response | Eager batch |
| 7 | `CodeSampleService.java` | `searchCodeSamples()` | 56 | Loop over search results for response population | Eager batch |
| 8 | `QuickSearchService.java` | `search()` | 57 | Loop over search results for response population | Eager batch |
| 9 | `CatalogService.java` | `buildSubjectList()` | 93 | Loop over entire keyword index for doc count computation | Eager batch |

#### R5.1: `DocumentService.java` — 3 call sites

**File:** `src/main/java/com/fvd/api/services/DocumentService.java`

The `DocumentService` calls `subjectDeriver.deriveSubject(path)` in:
- `getOrParseDocument()` (line 226): derives subject when parsing and caching a document
- `getDocumentByPathBrief()` (line 135): derives subject for brief document responses
- `searchDocuments()` (line 162): derives subject for each search result

**Change:** Inject `MetadataAwareSubjectResolver` and replace `SubjectDeriver` calls.

**Loading strategy:** Use **lazy per-document** (`resolveSubject(version, path)`) for `getOrParseDocument()` and `getDocumentByPathBrief()` (single document lookups). Use **eager batch** (`loadMetadataMap` + `resolveSubject(path, metadataMap)`) for `searchDocuments()` (batch results).

```java
@Inject
MetadataAwareSubjectResolver metadataResolver;

// In getOrParseDocument() — line 226:
// BEFORE:
String subject = subjectDeriver.deriveSubject(path);
// AFTER:
String subject = metadataResolver.resolveSubject(version, path);

// In getDocumentByPathBrief() — line 135:
// BEFORE:
String subject = subjectDeriver.deriveSubject(path);
// AFTER:
String subject = metadataResolver.resolveSubject(version, path);

// In searchDocuments() — line 162:
// BEFORE:
String derivedSubject = subjectDeriver.deriveSubject(fileResult.path);
// AFTER (batch-load once before the loop):
Map<String, DocumentMetadata> metadataMap = metadataResolver.loadMetadataMap(version);
// ...then in loop:
String derivedSubject = metadataResolver.resolveSubject(fileResult.path, metadataMap);
```

#### R5.2: `SearchService.java` — 2 call sites

**File:** `src/main/java/com/fvd/search/services/SearchService.java`

The `SearchService` calls `subjectDeriver.deriveSubject(path)` in:
- `getFileResults()` (line 79): derives subject for each file in keyword index during file search, used for subject filtering
- `searchCodeSamples()` (line 312): derives subject for each code sample entry, used for subject filtering

**Context:** Both call sites iterate over all entries in an index and derive subjects for filtering. These are hot paths that process the entire index on every search request.

**Change:** Inject `MetadataAwareSubjectResolver` and batch-load metadata per version. Since `SearchService` already caches the `KeywordIndex` per version, the metadata map should also be batch-loaded once per search call.

```java
@Inject
MetadataAwareSubjectResolver metadataResolver;

// In getFileResults() — line 79:
// BEFORE:
String derivedSubject = subjectDeriver.deriveSubject(file.path);
// AFTER (batch-load metadata once, pass into method):
// Add metadataMap parameter to getFileResults():
private List<FileSearchResult> getFileResults(KeywordIndex index,
        Map<String, String> stemmedToOriginal, String extension, String subject,
        Map<String, DocumentMetadata> metadataMap) {
    // ...
    for (FileKeywordEntry file : index.files) {
        // ...
        String derivedSubject = metadataResolver.resolveSubject(file.path, metadataMap);
        // ...
    }
}

// Caller in searchFiles() loads metadata batch:
Map<String, DocumentMetadata> metadataMap = metadataResolver.loadMetadataMap(version);
List<FileSearchResult> all = getFileResults(index, stemmedToOriginal, extension, subject, metadataMap);

// In searchCodeSamples() — line 312:
// BEFORE:
String derivedSubject = subjectDeriver.deriveSubject(sample.filePath);
// AFTER:
Map<String, DocumentMetadata> metadataMap = metadataResolver.loadMetadataMap(version);
// ...then in loop:
String derivedSubject = metadataResolver.resolveSubject(sample.filePath, metadataMap);
```

**Note:** The `searchFiles()` and `searchCodeSamples()` methods both use subject derivation for **filtering** — if these callers are not updated, queries like `GET /api/search/files?subject=security` will still use path-regex to determine whether a document belongs to the "security" subject, defeating the purpose of this feature.

#### R5.3: `RelatedDocumentService.java` — 1 call site

**File:** `src/main/java/com/fvd/api/services/RelatedDocumentService.java`

The `RelatedDocumentService` calls `subjectDeriver.deriveSubject(path)` in:
- `findRelatedDocuments()` (line 90): derives subject for each candidate document in the keyword index, used for subject filtering and for populating the `subject` field in `RelatedDocumentRef` responses

**Change:** Inject `MetadataAwareSubjectResolver` and batch-load metadata (this method iterates all files in the index).

```java
@Inject
MetadataAwareSubjectResolver metadataResolver;

// In findRelatedDocuments() — line 90:
// BEFORE:
String derivedSubject = subjectDeriver.deriveSubject(candidate.path);
// AFTER (batch-load metadata before the loop):
Map<String, DocumentMetadata> metadataMap = metadataResolver.loadMetadataMap(version);
// ...then in loop:
String derivedSubject = metadataResolver.resolveSubject(candidate.path, metadataMap);
```

#### R5.4: `CodeSampleService.java` — 1 call site

**File:** `src/main/java/com/fvd/api/services/CodeSampleService.java`

The `CodeSampleService` calls `subjectDeriver.deriveSubject(path)` in:
- `searchCodeSamples()` (line 56): derives subject for each code sample search result, used to populate the `subject` field in `CodeSampleResult` responses

**Change:** Inject `MetadataAwareSubjectResolver` and batch-load metadata (this method iterates search results).

```java
@Inject
MetadataAwareSubjectResolver metadataResolver;

// In searchCodeSamples() — line 56:
// BEFORE:
String derivedSubject = subjectDeriver.deriveSubject(csResult.path);
// AFTER (batch-load metadata before the loop):
Map<String, DocumentMetadata> metadataMap = metadataResolver.loadMetadataMap(version);
// ...then in loop:
String derivedSubject = metadataResolver.resolveSubject(csResult.path, metadataMap);
```

#### R5.5: `QuickSearchService.java` — 1 call site

**File:** `src/main/java/com/fvd/api/services/QuickSearchService.java`

The `QuickSearchService` calls `subjectDeriver.deriveSubject(path)` in:
- `search()` (line 57): derives subject for each file search result, used to populate the `subject` field in `SearchResultRef` responses

**Change:** Inject `MetadataAwareSubjectResolver` and batch-load metadata (this method iterates search results).

```java
@Inject
MetadataAwareSubjectResolver metadataResolver;

// In search() — line 57:
// BEFORE:
String derivedSubject = subjectDeriver.deriveSubject(fileResult.path);
// AFTER (batch-load metadata before the loop):
Map<String, DocumentMetadata> metadataMap = metadataResolver.loadMetadataMap(version);
// ...then in loop:
String derivedSubject = metadataResolver.resolveSubject(fileResult.path, metadataMap);
```

#### R5.6: `CatalogService.java` — 1 call site

**File:** `src/main/java/com/fvd/api/services/CatalogService.java`

The `CatalogService` calls `subjectDeriver.deriveSubject(path)` in:
- `buildSubjectList()` (line 93): derives subject for every file in the keyword index to compute per-subject document counts for the catalog endpoint

**This is the most critical caller** — this is the method that directly computes the subject counts shown by `GET /api/catalog`. Without updating this caller, the catalog endpoint will continue to show the broken 2,548-misc distribution.

**Change:** Inject `MetadataAwareSubjectResolver` and batch-load metadata (this method iterates the entire index).

```java
@Inject
MetadataAwareSubjectResolver metadataResolver;

// In buildSubjectList() — line 93:
// BEFORE:
for (FileKeywordEntry file : index.files) {
    String subject = subjectDeriver.deriveSubject(file.path);
    subjectDeriver.recordDocument(subject);
}
// AFTER:
Map<String, DocumentMetadata> metadataMap = metadataResolver.loadMetadataMap(version);
for (FileKeywordEntry file : index.files) {
    String subject = metadataResolver.resolveSubject(file.path, metadataMap);
    subjectDeriver.recordDocument(subject);
}
```

**Migration path for callers:**

| Service | Current | After |
|---------|---------|-------|
| `DocumentService` (single doc) | `subjectDeriver.deriveSubject(path)` | `metadataResolver.resolveSubject(version, path)` |
| `DocumentService` (search loop) | `subjectDeriver.deriveSubject(path)` | `metadataResolver.resolveSubject(path, metadataMap)` (batch) |
| `SearchService` (file search) | `subjectDeriver.deriveSubject(path)` | `metadataResolver.resolveSubject(path, metadataMap)` (batch) |
| `SearchService` (code sample) | `subjectDeriver.deriveSubject(path)` | `metadataResolver.resolveSubject(path, metadataMap)` (batch) |
| `RelatedDocumentService` | `subjectDeriver.deriveSubject(path)` | `metadataResolver.resolveSubject(path, metadataMap)` (batch) |
| `CodeSampleService` | `subjectDeriver.deriveSubject(path)` | `metadataResolver.resolveSubject(path, metadataMap)` (batch) |
| `QuickSearchService` | `subjectDeriver.deriveSubject(path)` | `metadataResolver.resolveSubject(path, metadataMap)` (batch) |
| `CatalogService` | `subjectDeriver.deriveSubject(path)` | `metadataResolver.resolveSubject(path, metadataMap)` (batch) |

Each service replaces its `SubjectDeriver` injection with `MetadataAwareSubjectResolver`. The `SubjectDeriver` itself remains injectable for tests and for the `CatalogService` which also uses `resetDocCounts()`, `recordDocument()`, and `getAllSubjects()`.

---

## Technical Design

### Metadata Loading Strategy

Multiple services need metadata to derive subjects. Two loading strategies are used depending on context:

**Option A: Lazy per-document.** Call `metadataResolver.resolveSubject(version, path)` which internally calls `documentMetadataStore.readByPath(version, path)` for each document. Simple, no memory overhead, but one SQL query per document. Used for single-document endpoints.

**Option B: Eager batch.** Call `metadataResolver.loadMetadataMap(version)` once and then `metadataResolver.resolveSubject(path, metadataMap)` in the loop. Better for bulk operations (search, catalog, related docs). Requires memory for all metadata.

**Decision:**
- Use **Option A** for `DocumentService.getOrParseDocument()` and `DocumentService.getDocumentByPathBrief()` (single document)
- Use **Option B** for all loop-based callers: `DocumentService.searchDocuments()`, `SearchService.getFileResults()`, `SearchService.searchCodeSamples()`, `RelatedDocumentService.findRelatedDocuments()`, `CodeSampleService.searchCodeSamples()`, `QuickSearchService.search()`, `CatalogService.buildSubjectList()`

### `ParsedDocument` Cache Invalidation

The `DocumentService` caches `ParsedDocument` objects in a `ConcurrentHashMap`. These cached objects contain the `subject` field derived at parse time. When Feature 76 changes subject classification logic, cached documents will have stale subjects until the cache is invalidated.

**Mitigation:** The existing `invalidateDocumentCache(version)` method already clears the cache during `CacheRefreshJob`. No additional changes are needed because:
- On first request after startup, there is no cache → fresh subjects are derived
- On cache refresh, the cache is explicitly invalidated
- Subject derivation happens at parse time, not at cache read time

### Classification Flow

The `MetadataAwareSubjectResolver` delegates to `SubjectDeriver.deriveSubject(filePath, metadata)` which implements the full classification priority chain. See Feature 76A for the classification flow diagram and algorithm details.

---

## Request/Response Examples

### Before Feature 76B (and Before Feature 76A Integration)

```
GET /api/catalog
```

```json
{
    "subjects": [
        { "name": "misc", "displayName": "Miscellaneous", "docCount": 2548 },
        { "name": "security", "displayName": "Security", "docCount": 23 },
        { "name": "rest-apis", "displayName": "REST APIs", "docCount": 6 },
        { "name": "data-persistence", "displayName": "Data & Persistence", "docCount": 12 },
        { "name": "getting-started", "displayName": "Getting Started", "docCount": 3 }
    ]
}
```

### After Feature 76B

```
GET /api/catalog
```

```json
{
    "subjects": [
        { "name": "misc", "displayName": "Miscellaneous", "docCount": 42 },
        { "name": "security", "displayName": "Security", "docCount": 48 },
        { "name": "rest-apis", "displayName": "REST APIs", "docCount": 35 },
        { "name": "core-concepts", "displayName": "Core Concepts", "docCount": 45 },
        { "name": "data-persistence", "displayName": "Data & Persistence", "docCount": 28 },
        { "name": "cloud", "displayName": "Cloud & Containers", "docCount": 18 },
        { "name": "observability", "displayName": "Observability", "docCount": 15 },
        { "name": "getting-started", "displayName": "Getting Started", "docCount": 22 },
        { "name": "messaging", "displayName": "Messaging", "docCount": 12 },
        { "name": "testing", "displayName": "Testing", "docCount": 8 },
        { "name": "tooling", "displayName": "Tooling", "docCount": 10 },
        { "name": "extensions", "displayName": "Extensions", "docCount": 15 }
    ]
}
```

### Example: Document Reclassification

**Before:** `GET /api/documents?path=virtual-threads.adoc`
```json
{
    "title": "Virtual Threads",
    "subject": "misc",
    "path": "virtual-threads.adoc"
}
```

**After:** (`:categories: web` → "rest-apis")
```json
{
    "title": "Virtual Threads",
    "subject": "rest-apis",
    "path": "virtual-threads.adoc"
}
```

### Example: Filtering by Subject Now Returns More Results

**Before:** `GET /api/search?keywords=oidc&subject=security` → 3 results
**After:** `GET /api/search?keywords=oidc&subject=security` → 12 results (because more docs are now correctly classified as "security")

---

## Implementation Notes

### Integration Order

Feature 76A **must be completed first**. This feature depends on the `deriveSubject(filePath, metadata)` overload and the mapping methods that 76A adds to `SubjectDeriver`.

Recommended implementation order within this feature:
1. Create `MetadataAwareSubjectResolver` service (R7)
2. Write tests for the resolver
3. Update `CatalogService` first (most critical — catalog doc counts)
4. Update remaining 5 services in any order
5. Update existing test mocks
6. Run integration test for catalog accuracy

### Updating 6 Service Files — Merge Conflict Risk

Updating 6 service files + their tests increases merge conflict risk. Mitigate by:
- Implementing `MetadataAwareSubjectResolver` first
- Then updating callers in separate commits per service
- Running `./gradlew test` after each service update

---

## Tasks

- [ ] Create `MetadataAwareSubjectResolver` service (R7)
- [ ] Update `DocumentService` — inject `MetadataAwareSubjectResolver`, update 3 call sites (R5.1)
- [ ] Update `SearchService` — inject `MetadataAwareSubjectResolver`, update 2 call sites (R5.2)
- [ ] Update `RelatedDocumentService` — inject `MetadataAwareSubjectResolver`, update 1 call site (R5.3)
- [ ] Update `CodeSampleService` — inject `MetadataAwareSubjectResolver`, update 1 call site (R5.4)
- [ ] Update `QuickSearchService` — inject `MetadataAwareSubjectResolver`, update 1 call site (R5.5)
- [ ] Update `CatalogService` — inject `MetadataAwareSubjectResolver`, update 1 call site (R5.6)
- [ ] Add unit tests for `MetadataAwareSubjectResolver`:
    - `resolveSubject(version, path)` delegates to `SubjectDeriver.deriveSubject(path, metadata)`
    - `resolveSubject(path, metadataMap)` uses pre-loaded map
    - Handles null metadata gracefully
- [ ] Update existing test mocks in `SearchServiceTest`, `RelatedDocumentServiceTest`, `CodeSampleServiceTest`, `QuickSearchServiceTest`, `CatalogServiceTest`, `DocumentServiceTest`, `DocumentServiceBatchTest` to use `MetadataAwareSubjectResolver` instead of `SubjectDeriver`
- [ ] Add integration test verifying catalog doc counts improve after re-indexing with metadata
- [ ] Verify all existing tests pass after integration
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /api/documents?path=virtual-threads.adoc` returns `"subject": "rest-apis"` instead of `"misc"`
2. `GET /api/catalog` shows "misc" with significantly fewer documents (target: < 100, down from 2,548)
3. `GET /api/search/files?subject=security` uses metadata-based classification for filtering (not just path-regex)
4. `GET /api/search/code-samples?subject=security` uses metadata-based classification for filtering
5. `GET /api/quick-search?keywords=oidc&subject=security` uses metadata-based classification for filtering
6. `GET /api/related?path=security-oidc.adoc&subject=security` uses metadata-based classification for filtering
7. `GET /api/code-samples?keywords=oidc` returns results with metadata-derived subjects
8. `MetadataAwareSubjectResolver.resolveSubject(version, path)` correctly loads metadata and delegates to `SubjectDeriver.deriveSubject(path, metadata)`
9. `MetadataAwareSubjectResolver.resolveSubject(path, metadataMap)` correctly uses pre-loaded metadata map
10. All 9 call sites across 6 services use `MetadataAwareSubjectResolver` instead of direct `SubjectDeriver.deriveSubject(path)` calls
11. All existing tests pass unchanged (or with updated mocks)
12. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| `DocumentMetadataStore.readAll()` returns empty map if Feature 75 indexing hasn't run yet | Low | Medium | Handle null/empty metadata gracefully — fall back to path-regex (existing behavior) |
| Cached `ParsedDocument` objects contain stale subjects after code deploy | Low | Medium | Cache is cleared on restart; `invalidateDocumentCache()` runs during refresh; first-request-after-deploy is fresh |
| Subject count changes may surprise existing API consumers | Medium | Low | This is a bugfix — improved accuracy is the goal; document the change in release notes |
| Performance: `DocumentMetadataStore.readAll()` called by multiple services per request | Medium | Low | SQLite `readAll()` loads all metadata into memory once per call; for hot paths consider caching the metadata map per version in `MetadataAwareSubjectResolver` |
| Performance: `DocumentMetadataStore.readByPath()` adds 1 SQL query per document retrieval | Low | Low | SQLite lookups by indexed `file_id` are <1ms; only used for single-document endpoints |
| Updating 6 service files + their tests increases merge conflict risk | Medium | Medium | Implement `MetadataAwareSubjectResolver` first, then update callers in separate commits per service |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create `MetadataAwareSubjectResolver` service | 1.0 |
| Integrate into `DocumentService` (3 call sites) | 1.5 |
| Integrate into `SearchService` (2 call sites) | 1.5 |
| Integrate into `RelatedDocumentService` (1 call site) | 0.5 |
| Integrate into `CodeSampleService` (1 call site) | 0.5 |
| Integrate into `QuickSearchService` (1 call site) | 0.5 |
| Integrate into `CatalogService` (1 call site) | 0.5 |
| Unit tests for `MetadataAwareSubjectResolver` | 1.0 |
| Update existing test mocks (7 test files) | 2.0 |
| Integration test for catalog accuracy | 1.0 |
| Verify existing tests pass | 0.5 |
| Run full test suite | 0.5 |
| **Total** | **~11.5 hours** |

---

## Files Modified

### New Production Files (1 file)
- `src/main/java/com/fvd/subject/services/MetadataAwareSubjectResolver.java` — centralizes metadata loading + subject derivation for all services (R7)

### Modified Production Files (6 files)
- `src/main/java/com/fvd/api/services/DocumentService.java` — replace `SubjectDeriver` usage with `MetadataAwareSubjectResolver` in `getOrParseDocument()` (line 226), `getDocumentByPathBrief()` (line 135), `searchDocuments()` (line 162)
- `src/main/java/com/fvd/search/services/SearchService.java` — replace `SubjectDeriver` usage with `MetadataAwareSubjectResolver` in `getFileResults()` (line 79), `searchCodeSamples()` (line 312)
- `src/main/java/com/fvd/api/services/RelatedDocumentService.java` — replace `SubjectDeriver` usage with `MetadataAwareSubjectResolver` in `findRelatedDocuments()` (line 90)
- `src/main/java/com/fvd/api/services/CodeSampleService.java` — replace `SubjectDeriver` usage with `MetadataAwareSubjectResolver` in `searchCodeSamples()` (line 56)
- `src/main/java/com/fvd/api/services/QuickSearchService.java` — replace `SubjectDeriver` usage with `MetadataAwareSubjectResolver` in `search()` (line 57)
- `src/main/java/com/fvd/api/services/CatalogService.java` — inject `MetadataAwareSubjectResolver`, use in `buildSubjectList()` (line 93); retain `SubjectDeriver` injection for `resetDocCounts()`, `recordDocument()`, `getAllSubjects()`

### New Test Files (1 file)
- `src/test/java/com/fvd/subject/services/MetadataAwareSubjectResolverTest.java` — unit tests for the resolver service

### Modified Test Files (7 files)
- `src/test/java/com/fvd/api/services/DocumentServiceTest.java` — update mocks to use `MetadataAwareSubjectResolver`
- `src/test/java/com/fvd/api/services/DocumentServiceBatchTest.java` — update mocks to use `MetadataAwareSubjectResolver`
- `src/test/java/com/fvd/search/services/SearchServiceTest.java` — update mocks to use `MetadataAwareSubjectResolver`
- `src/test/java/com/fvd/api/services/RelatedDocumentServiceTest.java` — update mocks to use `MetadataAwareSubjectResolver`
- `src/test/java/com/fvd/api/services/CatalogServiceTest.java` — update mocks to use `MetadataAwareSubjectResolver`
- `src/test/java/com/fvd/api/services/QuickSearchServiceTest.java` — update mocks (if exists; create if needed)
- `src/test/java/com/fvd/api/services/CodeSampleServiceTest.java` — update mocks (if exists; create if needed)

---

## Dependencies

- **Feature 75** (Parse & Index Document Metadata) — `DocumentMetadata` model and `DocumentMetadataStore` must be available
- **Feature 76A** (Metadata Classification Logic in SubjectDeriver) — the `deriveSubject(filePath, metadata)` overload, `CATEGORY_TO_SUBJECT` mapping, `TOPIC_KEYWORDS_TO_SUBJECT` mapping, `mapCategoryToSubject()`, and `mapTopicsToSubject()` methods must be implemented in `SubjectDeriver` before this feature can be started
- `SubjectConfig` — no changes needed (existing config structure supports this feature)
- `CacheRefreshJob` — no changes needed (existing cache refresh rebuilds indexes which now include metadata)

---

END OF FILE
