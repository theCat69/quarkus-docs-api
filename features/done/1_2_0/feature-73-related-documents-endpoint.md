# Feature 73: Related Documents Endpoint

> **Dependencies**: None. Uses existing in-memory `KeywordIndex` cache from `SearchService`. Compatible with all existing endpoints.

## Summary

After reading a document, an AI agent often needs to discover related or similar documents for deeper context. Currently, the only way to find neighbors is to perform another keyword search, which requires the agent to manually extract keywords from the document it just read. This feature adds a `GET /api/documents/related` endpoint that, given a document path, returns a ranked list of related documents computed from shared keyword overlap in the existing index. This enables a **graph-like navigation** pattern across the documentation corpus.

## User Story

As an **AI agent building context from Quarkus documentation**, I want to request documents related to a given document path and receive a ranked list of lightweight references with similarity scores, so that I can navigate from one document to its neighbors without manually crafting keyword queries.

## Motivation

### Current Behavior

An AI agent reads `security-overview.adoc` and wants to find related documents. It must:
1. Extract keywords from the content it just read (e.g., "oidc", "jwt", "authentication")
2. Perform a `GET /api/search/files?keywords=oidc jwt authentication` search
3. Filter out the source document from results
4. Hope the manually chosen keywords produce relevant neighbors

This is fragile, adds latency, and duplicates logic the server already has — the keyword index already knows every keyword associated with every document and how important each one is.

### Desired Behavior

```
GET /api/documents/related?path=security-overview.adoc&limit=5
```

Returns:

```json
{
    "results": [
        {
            "path": "security-oidc-code-flow-authentication.adoc",
            "title": "OpenID Connect Authorization Code Flow",
            "description": "Use OpenID Connect Authorization Code Flow to protect...",
            "subject": "security",
            "extension": "quarkus-core",
            "similarityScore": 0.82,
            "sharedKeywords": ["secur", "oidc", "authent", "token"]
        },
        {
            "path": "security-jwt.adoc",
            "title": "Using JWT RBAC",
            "description": "Secure your applications with JSON Web Tokens...",
            "subject": "security",
            "extension": "quarkus-core",
            "similarityScore": 0.71,
            "sharedKeywords": ["secur", "jwt", "token", "claim"]
        }
    ],
    "totalCount": 12,
    "returnedCount": 2
}
```

The agent gets related documents in a single call, ranked by how similar they are based on the same keyword index used for search.

### Two-Step Exploration Pattern for AI Agents

1. **Read**: `GET /api/documents?path=security-overview.adoc` — full document content
2. **Explore**: `GET /api/documents/related?path=security-overview.adoc` — related documents
3. **Drill down**: `GET /api/documents?path=security-oidc-code-flow-authentication.adoc` — full content of the most related doc

---

## Requirements

### R1: New `GET /api/documents/related` Endpoint

**File:** `src/main/java/com/fvd/api/resources/RelatedDocumentResource.java` (new file)

Create a new JAX-RS resource class following existing patterns:

```java
@Path("/api/documents/related")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document retrieval and search operations")
public class RelatedDocumentResource {

    private final RelatedDocumentService relatedDocumentService;
    private final CacheService cacheService;
    private final SubjectDeriver subjectDeriver;

    @GET
    @Operation(
            summary = "Find documents related to a given document",
            description = "Returns a ranked list of documents similar to the specified source document, " +
                    "computed from shared keyword overlap in the keyword index. " +
                    "Similarity is based on weighted cosine similarity of keyword vectors. " +
                    "Results are lightweight references (no full content) suitable for discovery."
    )
    public RelatedDocumentResponse getRelatedDocuments(
            @Parameter(description = "Quarkus version branch or tag. Defaults to 'main' if omitted.",
                    required = false, example = "main", schema = @Schema(defaultValue = "main"))
            @QueryParam("version") String version,

            @Parameter(description = "Path of the source document to find related documents for.",
                    required = true, example = "security-overview.adoc")
            @QueryParam("path") String path,

            @Parameter(description = "Filter results by subject (e.g., 'security', 'rest-apis')",
                    required = false, example = "security")
            @QueryParam("subject") String subject,

            @Parameter(description = "Filter results by extension (e.g., 'quarkus-core')",
                    required = false, example = "quarkus-core")
            @QueryParam("extension") String extension,

            @Parameter(description = "Maximum number of related documents to return (default 5, max 20)",
                    required = false, example = "5")
            @QueryParam("limit") Integer limit) {
        // Validate inputs
        String resolvedVersion = InputValidator.resolveVersion(version);
        InputValidator.validateVersionExists(resolvedVersion, cacheService.listCachedVersions());
        InputValidator.validatePath(path);
        InputValidator.validateSubjectExists(subject, subjectDeriver.getValidSubjectNames());
        int resolvedLimit = InputValidator.validateLimit(limit, 5, 20);

        return relatedDocumentService.findRelatedDocuments(
                resolvedVersion, path, subject, extension, resolvedLimit);
    }
}
```

**Key design decisions:**
- Separate resource class (`RelatedDocumentResource`) rather than adding to `DocumentResource`, because the semantics are different: this is a graph-traversal operation, not a document retrieval or keyword search.
- `path` is required (the source document).
- `limit` defaults to 5, max 20 (related documents are exploratory; agents rarely need more than 5-10).
- No `offset` parameter: pagination is not needed for a small, ranked list of related documents.
- No `brief` parameter: the response is always lightweight by design (no sections or code blocks).

### R2: New `RelatedDocumentService` Business Logic

**File:** `src/main/java/com/fvd/api/services/RelatedDocumentService.java` (new file)

```java
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class RelatedDocumentService {

    private final SearchService searchService;
    private final DocStore docStore;
    private final SubjectDeriver subjectDeriver;

    public RelatedDocumentResponse findRelatedDocuments(String version, String sourcePath,
                                                         String subjectFilter, String extensionFilter,
                                                         int limit) {
        // 1. Get the keyword index for this version (via SearchService's cached index)
        // 2. Find the source document's keyword vector
        // 3. Compute similarity against all other documents
        // 4. Apply filters, sort by similarity, take top N
        // 5. Enrich results with title and description
    }
}
```

### R3: Similarity Algorithm — Weighted Cosine Similarity

The core algorithm computes document similarity using the keyword vectors already stored in the `KeywordIndex`. Each `FileKeywordEntry` has a `List<KeywordScore>` where each `KeywordScore` has `word` (stemmed), `score` (int, weighted by source location), and `source` (filename/title/section/subtitle/body).

**Algorithm:**

1. **Build keyword vector for source document**: From `FileKeywordEntry.keywords`, create a `Map<String, Double>` where key = stemmed word, value = score. The score already encodes location weight (filename keywords score 10x, title 8x, etc.).

2. **For each candidate document** (excluding source):
   a. Build its keyword vector the same way.
   b. Compute **weighted cosine similarity**:
      ```
      similarity(A, B) = dot(A, B) / (||A|| * ||B||)
      ```
      Where `dot(A, B) = sum(A[w] * B[w])` for all shared words `w`, and `||A|| = sqrt(sum(A[w]^2))`.

3. **Collect shared keywords**: The words that appear in both vectors (the intersection), sorted by combined score descending.

4. **Filter**: Apply optional `subject` and `extension` filters using `FilterUtils.matchesFilter()`.

5. **Rank**: Sort candidates by similarity score descending, take top `limit`.

6. **Enrich**: For each result, read the doc content (first ~20 lines) to extract title and description using `DocumentTitleExtractor.extractTitle()` and the existing `extractDescription()` logic.

**Why cosine similarity over Jaccard index:**
- Cosine similarity respects the **magnitude** of keyword weights. Two documents sharing a keyword from their filenames (score=10) are more related than two sharing a body keyword (score=1).
- Jaccard only considers set membership (present/absent) and ignores how important each keyword is.
- The keyword scores already encode location hierarchy, so cosine similarity naturally prioritizes documents that share high-signal keywords.

**Implementation sketch:**

```java
private double computeCosineSimilarity(Map<String, Double> vectorA, Map<String, Double> vectorB) {
    double dotProduct = 0.0;
    double normA = 0.0;
    double normB = 0.0;

    for (Map.Entry<String, Double> entry : vectorA.entrySet()) {
        double a = entry.getValue();
        normA += a * a;
        Double b = vectorB.get(entry.getKey());
        if (b != null) {
            dotProduct += a * b;
        }
    }

    for (double b : vectorB.values()) {
        normB += b * b;
    }

    if (normA == 0 || normB == 0) {
        return 0.0;
    }

    return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
}
```

### R4: Access to Keyword Index from SearchService

The `KeywordIndex` is loaded and cached in `SearchService.indexCache` (a `ConcurrentHashMap<String, KeywordIndex>`). The `RelatedDocumentService` needs access to this cached index.

**Option A (preferred):** Add a public method to `SearchService` that exposes the cached index:

```java
/**
 * Returns the keyword index for a version, loading from SQLite and caching if needed.
 * Returns null if no index exists for the version.
 */
public KeywordIndex getKeywordIndex(String version) {
    return getOrBuildIndex(version);
}
```

This makes `getOrBuildIndex` accessible without exposing the cache directly. The method already handles lazy loading and caching.

**File:** `src/main/java/com/fvd/search/services/SearchService.java`

Change `getOrBuildIndex` visibility or add a public wrapper:

```java
// Add new public method (lines ~379):
public KeywordIndex getKeywordIndex(String version) {
    return getOrBuildIndex(version);
}
```

### R5: New DTO — `RelatedDocumentRef`

**File:** `src/main/java/com/fvd/api/dto/RelatedDocumentRef.java` (new file)

```java
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class RelatedDocumentRef {

    public String path;
    public String title;
    public String description;
    public String subject;
    public String extension;
    public double similarityScore;
    public List<String> sharedKeywords;

}
```

**Design notes:**
- Lightweight: no sections, no code blocks, no full content.
- `similarityScore` is 0.0-1.0 (cosine similarity).
- `sharedKeywords` lists the stemmed keywords that both documents share, sorted by combined weight descending. Capped at 10 keywords to keep the response compact.

### R6: New DTO — `RelatedDocumentResponse`

**File:** `src/main/java/com/fvd/api/dto/RelatedDocumentResponse.java` (new file)

```java
@SuperBuilder
@NoArgsConstructor
@RegisterForReflection
public class RelatedDocumentResponse extends PaginatedResponse<RelatedDocumentRef> {
}
```

Extends the existing `PaginatedResponse<T>` base class to inherit `results`, `totalCount`, and `returnedCount` fields.

### R7: Title and Description Extraction for Results

The `RelatedDocumentService` needs to extract title and description for each related document. This logic already exists in `DocumentService`:
- `DocumentTitleExtractor.extractTitle(content)` — static utility, already reusable.
- `DocumentService.extractDescription(content)` — currently a private method.

**Option A (preferred):** Extract `extractDescription` into a static utility method in `DocumentTitleExtractor` (or a new `DocumentMetadataExtractor` utility class), so both `DocumentService` and `RelatedDocumentService` can use it without duplication.

**Option B:** Have `RelatedDocumentService` call `docStore.read()` and duplicate the extraction logic.

**Recommendation:** Option A. Move `extractDescription` to a shared utility to maintain DRY principles. However, to minimize scope, Option B is acceptable for the initial implementation, with a follow-up refactoring task.

### R8: Error Handling

- If `path` is not found in the keyword index for the given version, return `404` with a `ProblemDetail` response: `"Document not found in index: {path}"`.
- If no keyword index exists for the version, return `404`: `"No keyword index available for version: {version}"`.
- If `path` is empty/null, return `400` (handled by `InputValidator.validatePath()`).
- The endpoint should never return the source document itself in the results.

### R9: Configuration

**File:** `src/main/resources/application.properties`

Add configurable defaults under the `search.related` prefix:

```properties
# Related documents configuration
search.related.default-limit=5
search.related.max-limit=20
search.related.min-similarity=0.05
search.related.max-shared-keywords=10
```

- `min-similarity`: minimum cosine similarity threshold. Documents below this score are excluded from results. Default 0.05 to filter out noise (documents sharing only one low-scoring body keyword).
- `max-shared-keywords`: maximum number of shared keywords to include in the response per result.

**File:** `src/main/java/com/fvd/search/SearchConfig.java`

Add a new `Related` interface:

```java
Related related();

interface Related {
    @WithDefault("5")
    int defaultLimit();

    @WithDefault("20")
    int maxLimit();

    @WithDefault("0.05")
    double minSimilarity();

    @WithDefault("10")
    int maxSharedKeywords();
}
```

---

## Response Schema

### Request

```
GET /api/documents/related?path=security-overview.adoc&version=main&limit=5&subject=security
```

### Success Response (200)

```json
{
    "results": [
        {
            "path": "security-oidc-code-flow-authentication.adoc",
            "title": "OpenID Connect Authorization Code Flow",
            "description": "Use OpenID Connect Authorization Code Flow to protect web applications...",
            "subject": "security",
            "extension": "quarkus-core",
            "similarityScore": 0.82,
            "sharedKeywords": ["secur", "oidc", "authent", "token", "provider"]
        },
        {
            "path": "security-jwt.adoc",
            "title": "Using JWT RBAC",
            "description": "Secure your applications with JSON Web Tokens...",
            "subject": "security",
            "extension": "quarkus-core",
            "similarityScore": 0.71,
            "sharedKeywords": ["secur", "jwt", "token", "claim"]
        },
        {
            "path": "security-keycloak-admin-client.adoc",
            "title": "Keycloak Admin Client",
            "description": "Use the Keycloak admin client to manage Keycloak...",
            "subject": "security",
            "extension": "quarkus-core",
            "similarityScore": 0.58,
            "sharedKeywords": ["secur", "keycloak", "authent"]
        }
    ],
    "totalCount": 8,
    "returnedCount": 3
}
```

### Error Responses

**400 — Invalid input:**
```json
{
    "type": "invalid-input",
    "title": "Invalid Input",
    "status": 400,
    "detail": "path must not be empty",
    "instance": "/api/documents/related"
}
```

**404 — Document not found in index:**
```json
{
    "type": "doc-not-found",
    "title": "Document Not Found",
    "status": 404,
    "detail": "Document not found in index: nonexistent.adoc",
    "instance": "/api/documents/related"
}
```

---

## Performance Considerations

### Time Complexity

For a version with `N` indexed documents and `K` average keywords per document:
- Building the source document's keyword vector: **O(K)**
- Computing similarity against all candidates: **O(N * K)** (iterating each candidate's keywords for dot product)
- Sorting results: **O(N log N)**
- Total: **O(N * K)** — linear in the number of documents

For a typical Quarkus version (~300 docs, ~50 keywords each), this is ~15,000 operations — well under 1ms.

### Memory

- No additional memory beyond what's already cached in `SearchService.indexCache`.
- The keyword vectors are computed on-the-fly from the existing `List<KeywordScore>` — no pre-computation or caching needed.
- The `Map<String, Double>` for the source document is temporary and GC'd after the request.

### I/O

- Title and description extraction requires `docStore.read()` for each result document. This reads from the filesystem but:
  - Only the first ~20 lines are scanned (not full parse).
  - Results are capped at `limit` (max 20), so at most 20 file reads.
  - Files are likely in OS page cache (already read during indexing).

### Optimization Opportunity (future)

If this endpoint becomes latency-critical, pre-compute a **keyword-to-document inverted index** (`Map<String, Set<String>>`) at index load time. This would allow O(K) lookups instead of scanning all N documents. However, the current O(N*K) approach is fast enough for ~300 docs.

---

## Tasks

- [ ] Add `Related` interface to `SearchConfig` with `defaultLimit`, `maxLimit`, `minSimilarity`, `maxSharedKeywords` properties
- [ ] Add `search.related.*` default values to `application.properties`
- [ ] Add public `getKeywordIndex(String version)` method to `SearchService` to expose the cached keyword index
- [ ] Create `RelatedDocumentRef` DTO in `com.fvd.api.dto` with fields: `path`, `title`, `description`, `subject`, `extension`, `similarityScore`, `sharedKeywords`
- [ ] Create `RelatedDocumentResponse` DTO in `com.fvd.api.dto` extending `PaginatedResponse<RelatedDocumentRef>`
- [ ] Create `RelatedDocumentService` in `com.fvd.api.services` with:
    - `findRelatedDocuments(version, path, subject, extension, limit)` method
    - Private `buildKeywordVector(FileKeywordEntry)` helper returning `Map<String, Double>`
    - Private `computeCosineSimilarity(Map, Map)` method
    - Private `extractSharedKeywords(Map, Map, int maxKeywords)` method
    - Title and description extraction for result enrichment
- [ ] Create `RelatedDocumentResource` in `com.fvd.api.resources` with:
    - `GET` endpoint at `/api/documents/related`
    - `@Parameter` and `@Schema` annotations on all query parameters
    - `@Operation` and `@APIResponse` OpenAPI annotations
    - `@Tag(name = "Documents")` to group with existing document endpoints
    - Input validation via `InputValidator`
- [ ] Handle error cases:
    - Source document not found in index → `DocNotFoundException` (404)
    - No keyword index for version → `DocNotFoundException` (404)
    - Invalid path → `InvalidInputException` (400)
- [ ] Unit tests for `RelatedDocumentService`:
    - `shouldReturnRelatedDocumentsRankedBySimilarity` — verify ordering by cosine similarity
    - `shouldExcludeSourceDocumentFromResults` — source path must not appear in results
    - `shouldFilterBySubject` — only matching subjects returned
    - `shouldFilterByExtension` — only matching extensions returned
    - `shouldRespectLimitParameter` — no more than `limit` results
    - `shouldReturnEmptyWhenNoRelatedDocuments` — document with unique keywords returns empty list
    - `shouldReturnEmptyWhenDocumentNotInIndex` — throws `DocNotFoundException`
    - `shouldComputeCorrectCosineSimilarity` — verify math with known vectors
    - `shouldExcludeDocumentsBelowMinSimilarity` — threshold filtering works
    - `shouldIncludeSharedKeywordsCappedAtMax` — shared keywords list respects max limit
- [ ] Unit tests for cosine similarity computation:
    - Identical vectors → similarity = 1.0
    - Orthogonal vectors (no shared keywords) → similarity = 0.0
    - Partial overlap → correct score between 0 and 1
    - Empty vector → similarity = 0.0
- [ ] Integration tests for `RelatedDocumentResource`:
    - `GET /api/documents/related?path=security-overview.adoc` → 200 with ranked results
    - `GET /api/documents/related?path=security-overview.adoc&subject=security` → only security documents
    - `GET /api/documents/related?path=security-overview.adoc&limit=3` → at most 3 results
    - `GET /api/documents/related?path=nonexistent.adoc` → 404
    - `GET /api/documents/related` (no path) → 400
    - `GET /api/documents/related?path=../etc/passwd` → 400 (path traversal blocked)
    - `GET /api/documents/related?path=security-overview.adoc&version=nonexistent` → 400 (unknown version)
    - Response contains `similarityScore` between 0.0 and 1.0
    - Response contains `sharedKeywords` as non-empty list for results with positive similarity
    - Source document path does not appear in results
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /api/documents/related?path=<validPath>` returns `200` with a ranked list of `RelatedDocumentRef` objects sorted by `similarityScore` descending
2. Each result contains `path`, `title`, `description`, `subject`, `extension`, `similarityScore` (0.0-1.0), and `sharedKeywords` (non-empty list)
3. The source document is never included in the results
4. `similarityScore` is computed using weighted cosine similarity on the keyword vectors from the existing `KeywordIndex`
5. Documents sharing high-weight keywords (filename/title) have higher similarity than documents sharing only body keywords
6. Optional `subject` and `extension` filters work correctly and are validated
7. `limit` parameter defaults to 5, max 20, and is enforced
8. `version` parameter defaults to `main`
9. `404` returned when the source document path is not found in the keyword index
10. `400` returned for missing/invalid `path` parameter
11. All query parameters have `@Parameter` and `@Schema` OpenAPI annotations
12. Results below `search.related.min-similarity` threshold are excluded
13. `sharedKeywords` list is capped at `search.related.max-shared-keywords`
14. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| O(N*K) scan too slow for very large doc sets (1000+) | Low | Medium | Current Quarkus docs are ~300 files. If needed, build an inverted index (`word → Set<filePath>`) at index load time to reduce to O(K) per query |
| Cosine similarity produces unexpected rankings (e.g., generic docs like "getting-started" match everything) | Medium | Low | Documents with many common keywords (stop-word-like) get diluted by the cosine normalization. The `min-similarity` threshold filters noise. Can be tuned via config |
| Title/description extraction adds I/O latency per result | Low | Low | Only reads first ~20 lines per file. Capped at max 20 files. Files likely in OS page cache. Could pre-compute and cache title/description in keyword index in a future enhancement |
| Exposing `getKeywordIndex()` on `SearchService` leaks internal data structure | Low | Low | The `KeywordIndex` is already a simple POJO with public fields. Read-only access is safe. Method is used only by `RelatedDocumentService` |
| New endpoint increases API surface area | Low | Low | Follows existing patterns. Grouped under same `Documents` tag. Non-breaking addition |
| Subject/extension filters may eliminate all candidates, returning empty results without explanation | Medium | Low | Return `totalCount: 0` with empty `results`. The AI agent can retry without filters. Consider adding a `filteredCount` field in a future enhancement |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| `SearchConfig.Related` interface + `application.properties` | 0.25 |
| `SearchService.getKeywordIndex()` public method | 0.25 |
| `RelatedDocumentRef` and `RelatedDocumentResponse` DTOs | 0.5 |
| `RelatedDocumentService` (similarity algorithm + enrichment) | 2.0 |
| `RelatedDocumentResource` (endpoint + OpenAPI + validation) | 1.0 |
| Extract description utility (optional DRY refactor) | 0.5 |
| Unit tests for `RelatedDocumentService` (10+ test methods) | 2.0 |
| Unit tests for cosine similarity (4 test methods) | 0.5 |
| Integration tests for `RelatedDocumentResource` (10+ test methods) | 1.5 |
| Run full test suite and fix regressions | 0.5 |
| **Total** | **~9.0 hours** |

---

## Files Modified

### Production Code — New Files (4 files)
- `src/main/java/com/fvd/api/resources/RelatedDocumentResource.java` — JAX-RS endpoint with OpenAPI annotations
- `src/main/java/com/fvd/api/services/RelatedDocumentService.java` — similarity algorithm and result enrichment
- `src/main/java/com/fvd/api/dto/RelatedDocumentRef.java` — lightweight related document DTO
- `src/main/java/com/fvd/api/dto/RelatedDocumentResponse.java` — paginated response wrapper

### Production Code — Modified Files (3 files)
- `src/main/java/com/fvd/search/services/SearchService.java` — add `getKeywordIndex()` public method
- `src/main/java/com/fvd/search/SearchConfig.java` — add `Related` config interface
- `src/main/resources/application.properties` — add `search.related.*` defaults

### Test Code — New Files (estimated 2 files)
- `src/test/java/com/fvd/api/services/RelatedDocumentServiceTest.java` — unit tests for similarity logic
- `src/test/java/com/fvd/api/resources/RelatedDocumentResourceTest.java` — integration tests for endpoint

---

END OF FILE
