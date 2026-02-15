# Feature 76: Metadata-Based Subject Classification

> **Dependencies**: Feature 75 (Parse & Index Document Metadata). Requires `DocumentMetadata` model and `DocumentMetadataStore` to be available.

## Summary

The current `SubjectDeriver` uses file-path regex patterns to classify documents into subjects. This approach is fundamentally broken: out of ~2,800 indexed documents, 2,548 are classified as "misc" while categories like "rest-apis" have only 6 documents. The root cause is that path-based heuristics cannot capture the semantic meaning of a document — e.g., `security-oidc-code-flow-authentication.adoc` matches the security regex, but `virtual-threads.adoc` (which is categorized as `web` in its metadata) falls through to "misc". This feature replaces the path-regex approach with a metadata-driven classification strategy that uses `:categories:` as the primary source, `:topics:` as secondary, and falls back to path-regex only for quarkiverse docs that lack metadata.

## User Story

As an **AI agent consuming the API through an MCP server**, I want document subject classifications to be accurate and comprehensive so that when I filter by `subject=security` I get all 40+ security-related docs instead of just the 6 whose filenames happen to contain "security", and when I browse the catalog I see meaningful document counts per subject instead of 2,548 docs under "misc".

## Motivation

### Current Behavior

`GET /api/catalog` returns subject counts like:

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

The `SubjectDeriver.deriveSubject("virtual-threads.adoc")` returns `"misc"` because no regex pattern matches "virtual-threads". But the document has `:categories: web` and `:topics: rest,resteasy-reactive,virtual-threads` — it should be classified as "rest-apis".

### Desired Behavior

After this feature, `GET /api/catalog` returns:

```json
{
    "subjects": [
        { "name": "misc", "displayName": "Miscellaneous", "docCount": 42 },
        { "name": "security", "displayName": "Security", "docCount": 48 },
        { "name": "rest-apis", "displayName": "REST APIs", "docCount": 35 },
        { "name": "data-persistence", "displayName": "Data & Persistence", "docCount": 28 },
        { "name": "getting-started", "displayName": "Getting Started", "docCount": 22 },
        { "name": "core-concepts", "displayName": "Core Concepts", "docCount": 45 },
        { "name": "cloud", "displayName": "Cloud & Containers", "docCount": 18 },
        { "name": "observability", "displayName": "Observability", "docCount": 15 }
    ]
}
```

The "misc" count drops from 2,548 to ~42 (only quarkiverse docs without metadata that don't match any path regex).

### Classification Priority Chain

```
1. Exact path overrides (existing, from config)     → highest priority
2. Glob pattern overrides (existing, from config)    → second priority
3. :categories: metadata mapping (NEW)               → primary classification source
4. :topics: metadata analysis (NEW)                  → fallback when categories don't match
5. Regex path patterns (existing)                    → last resort for docs without metadata
6. Default "misc"                                    → absolute fallback
```

---

## Scope / Requirements

### R1: Define Category-to-Subject Mapping Table

The `:categories:` attribute uses a fixed vocabulary defined by the Quarkus documentation team. These map directly to API subjects:

| Document `:categories:` value | API Subject | Notes |
|------------------------------|-------------|-------|
| `getting-started` | `getting-started` | Direct 1:1 |
| `core` | `core-concepts` | CDI, lifecycle, config |
| `web` | `rest-apis` | REST, HTTP, web frameworks |
| `data` | `data-persistence` | Hibernate, Panache, databases |
| `security` | `security` | Direct 1:1 |
| `messaging` | `messaging` | Kafka, AMQP, reactive messaging |
| `cloud` | `cloud` | Kubernetes, Docker, OpenShift |
| `observability` | `observability` | Metrics, health, tracing |
| `tooling` | `tooling` | CLI, Dev Services, IDE |
| `compatibility` | `core-concepts` | Migration, compatibility → core |
| `writing-extensions` | `extensions` | Extension authoring |
| `miscellaneous` | `misc` | Catch-all in source docs |
| `integration` | `messaging` | Integration patterns → messaging |
| `serialization` | `rest-apis` | JSON/XML serialization → web |
| `alternative-languages` | `core-concepts` | Kotlin, Scala → core |
| `business-automation` | `extensions` | Drools, Kogito → extensions |

**Implementation:** Store this mapping as a static `Map<String, String>` in `SubjectDeriver` or in a new configuration interface.

**Multi-category resolution:** When a document has multiple categories (e.g., `:categories: security,web`), use the **first category** as the primary subject. Rationale: the Quarkus docs team lists the most relevant category first.

### R2: Define Topic-to-Subject Fallback Mapping

When `:categories:` is absent or doesn't map to a known subject, fall back to `:topics:` analysis. Topics are more granular and require keyword-based mapping:

| Topic keyword (contains) | API Subject |
|--------------------------|-------------|
| `rest`, `resteasy`, `http`, `servlet`, `websocket`, `graphql` | `rest-apis` |
| `security`, `oidc`, `jwt`, `oauth`, `keycloak`, `auth` | `security` |
| `hibernate`, `panache`, `jpa`, `jdbc`, `datasource`, `database`, `sql`, `nosql`, `mongodb`, `redis` | `data-persistence` |
| `kafka`, `amqp`, `messaging`, `reactive-messaging`, `rabbitmq`, `pulsar` | `messaging` |
| `kubernetes`, `openshift`, `docker`, `container`, `cloud`, `aws`, `azure`, `gcp` | `cloud` |
| `metrics`, `health`, `tracing`, `logging`, `opentelemetry`, `micrometer` | `observability` |
| `test`, `junit`, `mock`, `testing` | `testing` |
| `cli`, `dev-services`, `devmode`, `ide`, `maven`, `gradle`, `quarkus-cli` | `tooling` |
| `cdi`, `config`, `lifecycle`, `injection`, `bean`, `native`, `graalvm` | `core-concepts` |
| `getting-started`, `quickstart`, `tutorial` | `getting-started` |
| `extension`, `quarkiverse` | `extensions` |

**Algorithm:** For each topic tag, check if it matches any keyword in the mapping. If a topic matches, use that subject. If multiple topics match different subjects, use the subject with the most topic matches (majority vote).

### R3: Modify `SubjectDeriver.deriveSubject()` to Use Metadata

**File:** `src/main/java/com/fvd/subject/services/SubjectDeriver.java`

Add a new overloaded method and modify the derivation pipeline:

```java
/**
 * Derive subject using document metadata (primary) with path-regex fallback.
 *
 * @param filePath the file path
 * @param metadata the document metadata (may be null or empty)
 * @return the derived subject name
 */
public String deriveSubject(String filePath, DocumentMetadata metadata) {
    if (!config.enabled()) {
        return DEFAULT_SUBJECT;
    }
    if (filePath == null || filePath.isBlank()) {
        return DEFAULT_SUBJECT;
    }

    String normalizedPath = normalizePath(filePath);

    // 1. Check exact overrides (existing)
    // ...

    // 2. Check glob pattern overrides (existing)
    // ...

    // 3. NEW: Check :categories: metadata
    if (metadata != null && metadata.hasCategories()) {
        String subject = mapCategoryToSubject(metadata.getCategories());
        if (subject != null) {
            log.trace("Path '{}' classified by categories {} -> '{}'",
                    filePath, metadata.getCategories(), subject);
            return subject;
        }
    }

    // 4. NEW: Check :topics: metadata
    if (metadata != null && metadata.hasTopics()) {
        String subject = mapTopicsToSubject(metadata.getTopics());
        if (subject != null) {
            log.trace("Path '{}' classified by topics {} -> '{}'",
                    filePath, metadata.getTopics(), subject);
            return subject;
        }
    }

    // 5. Check regex patterns (existing fallback)
    // ...

    // 6. Default
    return DEFAULT_SUBJECT;
}
```

The existing `deriveSubject(String filePath)` method remains for backward compatibility and continues to use path-regex only. Callers that have metadata should use the new overload.

### R4: Modify `deriveSubjects(List<String> filePaths)` to Accept Metadata Map

**File:** `src/main/java/com/fvd/subject/services/SubjectDeriver.java`

Add an overloaded batch method:

```java
/**
 * Derive subjects for multiple file paths using metadata when available.
 *
 * @param filePaths the list of file paths
 * @param metadataByPath map of file path → DocumentMetadata (from DocumentMetadataStore)
 * @return a map from file path to subject name
 */
public Map<String, String> deriveSubjects(List<String> filePaths,
                                           Map<String, DocumentMetadata> metadataByPath) {
    Map<String, String> result = new HashMap<>();
    for (String filePath : filePaths) {
        DocumentMetadata metadata = metadataByPath.get(filePath);
        String subject = deriveSubject(filePath, metadata);
        result.put(filePath, subject);
    }
    return result;
}
```

### R5: Integrate Metadata into ALL `deriveSubject()` Callers

> **IMPORTANT:** `subjectDeriver.deriveSubject(path)` is called in **8 locations across 6 service files**. ALL callers must be updated to use the new `deriveSubject(path, metadata)` overload to ensure consistent classification across the entire API surface.

#### R5.1: `DocumentService.java` — 3 call sites

**File:** `src/main/java/com/fvd/api/services/DocumentService.java`

The `DocumentService` calls `subjectDeriver.deriveSubject(path)` in:
- `getOrParseDocument()` (line 226): derives subject when parsing and caching a document
- `getDocumentByPathBrief()` (line 135): derives subject for brief document responses
- `searchDocuments()` (line 162): derives subject for each search result

**Change:** Inject `DocumentMetadataStore` and pass metadata to the new overload.

**Loading strategy:** Use **lazy per-document** (`readByPath`) for `getOrParseDocument()` and `getDocumentByPathBrief()` (single document lookups). Use **eager batch** (`readAll`) for `searchDocuments()` (batch results).

```java
@Inject
DocumentMetadataStore documentMetadataStore;

// In getOrParseDocument() — line 226:
// BEFORE:
String subject = subjectDeriver.deriveSubject(path);
// AFTER:
DocumentMetadata metadata = documentMetadataStore.readByPath(version, path).orElse(null);
String subject = subjectDeriver.deriveSubject(path, metadata);

// In getDocumentByPathBrief() — line 135:
// BEFORE:
String subject = subjectDeriver.deriveSubject(path);
// AFTER:
DocumentMetadata metadata = documentMetadataStore.readByPath(version, path).orElse(null);
String subject = subjectDeriver.deriveSubject(path, metadata);

// In searchDocuments() — line 162:
// BEFORE:
String derivedSubject = subjectDeriver.deriveSubject(fileResult.path);
// AFTER (batch-load once before the loop):
Map<String, DocumentMetadata> metadataMap = documentMetadataStore.readAll(version);
// ...then in loop:
DocumentMetadata metadata = metadataMap.get(fileResult.path);
String derivedSubject = subjectDeriver.deriveSubject(fileResult.path, metadata);
```

#### R5.2: `SearchService.java` — 2 call sites

**File:** `src/main/java/com/fvd/search/services/SearchService.java`

The `SearchService` calls `subjectDeriver.deriveSubject(path)` in:
- `getFileResults()` (line 79): derives subject for each file in keyword index during file search, used for subject filtering
- `searchCodeSamples()` (line 312): derives subject for each code sample entry, used for subject filtering

**Context:** Both call sites iterate over all entries in an index and derive subjects for filtering. These are hot paths that process the entire index on every search request.

**Change:** Inject `DocumentMetadataStore` and batch-load metadata per version. Since `SearchService` already caches the `KeywordIndex` per version, the metadata map should also be batch-loaded once per search call.

```java
@Inject
DocumentMetadataStore documentMetadataStore;

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
        DocumentMetadata metadata = metadataMap.get(file.path);
        String derivedSubject = subjectDeriver.deriveSubject(file.path, metadata);
        // ...
    }
}

// Caller in searchFiles() loads metadata batch:
Map<String, DocumentMetadata> metadataMap = documentMetadataStore.readAll(version);
List<FileSearchResult> all = getFileResults(index, stemmedToOriginal, extension, subject, metadataMap);

// In searchCodeSamples() — line 312:
// BEFORE:
String derivedSubject = subjectDeriver.deriveSubject(sample.filePath);
// AFTER:
Map<String, DocumentMetadata> metadataMap = documentMetadataStore.readAll(version);
// ...then in loop:
DocumentMetadata metadata = metadataMap.get(sample.filePath);
String derivedSubject = subjectDeriver.deriveSubject(sample.filePath, metadata);
```

**Note:** The `searchFiles()` and `searchCodeSamples()` methods both use subject derivation for **filtering** — if these callers are not updated, queries like `GET /api/search/files?subject=security` will still use path-regex to determine whether a document belongs to the "security" subject, defeating the purpose of this feature.

#### R5.3: `RelatedDocumentService.java` — 1 call site

**File:** `src/main/java/com/fvd/api/services/RelatedDocumentService.java`

The `RelatedDocumentService` calls `subjectDeriver.deriveSubject(path)` in:
- `findRelatedDocuments()` (line 90): derives subject for each candidate document in the keyword index, used for subject filtering and for populating the `subject` field in `RelatedDocumentRef` responses

**Change:** Inject `DocumentMetadataStore` and batch-load metadata (this method iterates all files in the index).

```java
@Inject
DocumentMetadataStore documentMetadataStore;

// In findRelatedDocuments() — line 90:
// BEFORE:
String derivedSubject = subjectDeriver.deriveSubject(candidate.path);
// AFTER (batch-load metadata before the loop):
Map<String, DocumentMetadata> metadataMap = documentMetadataStore.readAll(version);
// ...then in loop:
DocumentMetadata metadata = metadataMap.get(candidate.path);
String derivedSubject = subjectDeriver.deriveSubject(candidate.path, metadata);
```

#### R5.4: `CodeSampleService.java` — 1 call site

**File:** `src/main/java/com/fvd/api/services/CodeSampleService.java`

The `CodeSampleService` calls `subjectDeriver.deriveSubject(path)` in:
- `searchCodeSamples()` (line 56): derives subject for each code sample search result, used to populate the `subject` field in `CodeSampleResult` responses

**Change:** Inject `DocumentMetadataStore` and batch-load metadata (this method iterates search results).

```java
@Inject
DocumentMetadataStore documentMetadataStore;

// In searchCodeSamples() — line 56:
// BEFORE:
String derivedSubject = subjectDeriver.deriveSubject(csResult.path);
// AFTER (batch-load metadata before the loop):
Map<String, DocumentMetadata> metadataMap = documentMetadataStore.readAll(version);
// ...then in loop:
DocumentMetadata metadata = metadataMap.get(csResult.path);
String derivedSubject = subjectDeriver.deriveSubject(csResult.path, metadata);
```

#### R5.5: `QuickSearchService.java` — 1 call site

**File:** `src/main/java/com/fvd/api/services/QuickSearchService.java`

The `QuickSearchService` calls `subjectDeriver.deriveSubject(path)` in:
- `search()` (line 57): derives subject for each file search result, used to populate the `subject` field in `SearchResultRef` responses

**Change:** Inject `DocumentMetadataStore` and batch-load metadata (this method iterates search results).

```java
@Inject
DocumentMetadataStore documentMetadataStore;

// In search() — line 57:
// BEFORE:
String derivedSubject = subjectDeriver.deriveSubject(fileResult.path);
// AFTER (batch-load metadata before the loop):
Map<String, DocumentMetadata> metadataMap = documentMetadataStore.readAll(version);
// ...then in loop:
DocumentMetadata metadata = metadataMap.get(fileResult.path);
String derivedSubject = subjectDeriver.deriveSubject(fileResult.path, metadata);
```

#### R5.6: `CatalogService.java` — 1 call site

**File:** `src/main/java/com/fvd/api/services/CatalogService.java`

The `CatalogService` calls `subjectDeriver.deriveSubject(path)` in:
- `buildSubjectList()` (line 93): derives subject for every file in the keyword index to compute per-subject document counts for the catalog endpoint

**This is the most critical caller** — this is the method that directly computes the subject counts shown by `GET /api/catalog`. Without updating this caller, the catalog endpoint will continue to show the broken 2,548-misc distribution.

**Change:** Inject `DocumentMetadataStore` and batch-load metadata (this method iterates the entire index).

```java
@Inject
DocumentMetadataStore documentMetadataStore;

// In buildSubjectList() — line 93:
// BEFORE:
for (FileKeywordEntry file : index.files) {
    String subject = subjectDeriver.deriveSubject(file.path);
    subjectDeriver.recordDocument(subject);
}
// AFTER:
Map<String, DocumentMetadata> metadataMap = documentMetadataStore.readAll(version);
for (FileKeywordEntry file : index.files) {
    DocumentMetadata metadata = metadataMap.get(file.path);
    String subject = subjectDeriver.deriveSubject(file.path, metadata);
    subjectDeriver.recordDocument(subject);
}
```

### R6: Add Category-to-Subject and Topic-to-Subject Mapping Constants

**Approach:** Add mapping logic as private methods in `SubjectDeriver`:

```java
private static final Map<String, String> CATEGORY_TO_SUBJECT = Map.ofEntries(
    Map.entry("getting-started", "getting-started"),
    Map.entry("core", "core-concepts"),
    Map.entry("web", "rest-apis"),
    Map.entry("data", "data-persistence"),
    Map.entry("security", "security"),
    Map.entry("messaging", "messaging"),
    Map.entry("cloud", "cloud"),
    Map.entry("observability", "observability"),
    Map.entry("tooling", "tooling"),
    Map.entry("compatibility", "core-concepts"),
    Map.entry("writing-extensions", "extensions"),
    Map.entry("miscellaneous", "misc"),
    Map.entry("integration", "messaging"),
    Map.entry("serialization", "rest-apis"),
    Map.entry("alternative-languages", "core-concepts"),
    Map.entry("business-automation", "extensions")
);

private String mapCategoryToSubject(List<String> categories) {
    for (String category : categories) {
        String subject = CATEGORY_TO_SUBJECT.get(category.toLowerCase().trim());
        if (subject != null) {
            return subject;
        }
    }
    return null; // No category matched — fall through to topics
}
```

For topics, use a keyword-matching approach:

```java
private static final Map<String, String> TOPIC_KEYWORDS_TO_SUBJECT = Map.ofEntries(
    Map.entry("rest", "rest-apis"),
    Map.entry("resteasy", "rest-apis"),
    Map.entry("http", "rest-apis"),
    Map.entry("servlet", "rest-apis"),
    Map.entry("websocket", "rest-apis"),
    Map.entry("graphql", "rest-apis"),
    Map.entry("security", "security"),
    Map.entry("oidc", "security"),
    Map.entry("jwt", "security"),
    Map.entry("oauth", "security"),
    Map.entry("keycloak", "security"),
    Map.entry("hibernate", "data-persistence"),
    Map.entry("panache", "data-persistence"),
    Map.entry("jpa", "data-persistence"),
    Map.entry("jdbc", "data-persistence"),
    Map.entry("datasource", "data-persistence"),
    Map.entry("database", "data-persistence"),
    Map.entry("mongodb", "data-persistence"),
    Map.entry("redis", "data-persistence"),
    Map.entry("kafka", "messaging"),
    Map.entry("amqp", "messaging"),
    Map.entry("messaging", "messaging"),
    Map.entry("reactive-messaging", "messaging"),
    Map.entry("rabbitmq", "messaging"),
    Map.entry("kubernetes", "cloud"),
    Map.entry("openshift", "cloud"),
    Map.entry("docker", "cloud"),
    Map.entry("container", "cloud"),
    Map.entry("aws", "cloud"),
    Map.entry("azure", "cloud"),
    Map.entry("metrics", "observability"),
    Map.entry("health", "observability"),
    Map.entry("tracing", "observability"),
    Map.entry("opentelemetry", "observability"),
    Map.entry("micrometer", "observability"),
    Map.entry("logging", "observability"),
    Map.entry("test", "testing"),
    Map.entry("testing", "testing"),
    Map.entry("cdi", "core-concepts"),
    Map.entry("config", "core-concepts"),
    Map.entry("lifecycle", "core-concepts"),
    Map.entry("native", "core-concepts"),
    Map.entry("graalvm", "core-concepts"),
    Map.entry("getting-started", "getting-started"),
    Map.entry("extension", "extensions"),
    Map.entry("quarkiverse", "extensions"),
    Map.entry("cli", "tooling"),
    Map.entry("dev-services", "tooling"),
    Map.entry("maven", "tooling"),
    Map.entry("gradle", "tooling")
);

private String mapTopicsToSubject(List<String> topics) {
    // Count votes per subject using LinkedHashMap to preserve insertion order for deterministic tie-breaking
    Map<String, Integer> votes = new LinkedHashMap<>();
    for (String topic : topics) {
        String subject = TOPIC_KEYWORDS_TO_SUBJECT.get(topic.toLowerCase().trim());
        if (subject != null) {
            votes.merge(subject, 1, Integer::sum);
        }
    }
    if (votes.isEmpty()) {
        return null;
    }
    // Return subject with most votes; on tie, the subject that was first encountered
    // in topic iteration order wins (deterministic via LinkedHashMap insertion order)
    return votes.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
}
```

> **IMPORTANT (Deterministic Tie-Breaking):** The `votes` map MUST be a `LinkedHashMap`, NOT a `HashMap`. When two subjects have equal vote counts, `Stream.max()` returns the first element encountered in iteration order. With `HashMap`, iteration order is non-deterministic and JVM-dependent, meaning the same document could be classified differently across restarts or JVM versions. Using `LinkedHashMap` ensures that tie-breaking is deterministic: the subject whose first matching topic appears earliest in the `:topics:` list wins. This matches the convention that the document author lists the most relevant topic first.

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

### Classification Flow Diagram

```
deriveSubject(filePath, metadata)
    │
    ├── 1. Exact override? ──yes──▶ return override subject
    │
    ├── 2. Glob override? ──yes──▶ return glob subject
    │
    ├── 3. metadata.hasCategories()? ──yes──▶ mapCategoryToSubject()
    │       │                                    │
    │       │                              mapped? ──yes──▶ return subject
    │       │                                    │
    │       │                              no ───▶ continue
    │
    ├── 4. metadata.hasTopics()? ──yes──▶ mapTopicsToSubject()
    │       │                                 │
    │       │                           mapped? ──yes──▶ return subject
    │       │                                 │
    │       │                           no ───▶ continue
    │
    ├── 5. Regex path match? ──yes──▶ return regex subject
    │
    └── 6. return "misc"
```

### Complete Caller Inventory

All call sites for `subjectDeriver.deriveSubject()` across the codebase:

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

### Backward Compatibility

The existing `deriveSubject(String filePath)` method is NOT modified. It continues to use path-regex only. This ensures:
- All existing callers work unchanged
- Tests pass without modification
- The new metadata-aware path is opt-in via the new overload

Over time, callers should migrate to `deriveSubject(filePath, metadata)`. The old method can be deprecated in a future release.

### Metadata Loading Strategy

Multiple services need metadata to derive subjects. Two loading strategies are used depending on context:

**Option A: Lazy per-document.** Call `documentMetadataStore.readByPath(version, path)` for each document. Simple, no memory overhead, but one SQL query per document. Used for single-document endpoints.

**Option B: Eager batch.** Call `documentMetadataStore.readAll(version)` once and cache the map. Better for bulk operations (search, catalog, related docs). Requires memory for all metadata.

**Decision:**
- Use **Option A** for `DocumentService.getDocumentByPath()` and `DocumentService.getDocumentByPathBrief()` (single document)
- Use **Option B** for all loop-based callers: `DocumentService.searchDocuments()`, `SearchService.getFileResults()`, `SearchService.searchCodeSamples()`, `RelatedDocumentService.findRelatedDocuments()`, `CodeSampleService.searchCodeSamples()`, `QuickSearchService.search()`, `CatalogService.buildSubjectList()`

### No Config Changes Required

The `CATEGORY_TO_SUBJECT` mapping is defined as a static constant in `SubjectDeriver`. Rationale:
- The Quarkus docs category vocabulary is stable (defined by the Quarkus team)
- Making it configurable adds complexity without benefit (categories won't change without a code update anyway)
- If categories change, updating the constant is a one-line change

---

## Request/Response Examples

### Before Feature 76

```
GET /api/catalog
```

```json
{
    "subjects": [
        { "name": "misc", "displayName": "Miscellaneous", "docCount": 2548 },
        { "name": "security", "displayName": "Security", "docCount": 23 },
        { "name": "rest-apis", "displayName": "REST APIs", "docCount": 6 }
    ]
}
```

### After Feature 76

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

### Category Priority Over Topics

When a document has both `:categories: security,web` and `:topics: rest,security,oidc`, the category `security` takes precedence. The first category in the list wins. This matches the Quarkus docs convention where the primary category is listed first.

### Topic Majority Vote — Deterministic Tie-Breaking

When deriving from topics, a document with `:topics: rest,security,oidc,jwt` would have:
- `rest` → rest-apis (1 vote)
- `security` → security (1 vote)
- `oidc` → security (1 vote)
- `jwt` → security (1 vote)

Security wins with 3 votes. This correctly classifies OIDC-related REST security docs under "security".

**Tie-breaking rule:** When two or more subjects have the same vote count, the subject whose first matching topic appears **earliest in the `:topics:` list** wins. This is guaranteed by using a `LinkedHashMap` for vote counting, which preserves insertion order. Example: for `:topics: rest,kafka`, `rest-apis` and `messaging` each have 1 vote, but `rest-apis` was inserted first (because `rest` appears before `kafka`), so `rest-apis` wins.

### Quarkiverse Docs Fallback

Quarkiverse extension docs do NOT have `:categories:` or `:topics:` attributes. They use Antora playbook structure. For these docs:
1. Metadata is `null` or `DocumentMetadata.empty()`
2. Category/topic mapping returns `null`
3. Falls through to path-regex matching
4. If no regex matches, falls to "misc"

This is acceptable because quarkiverse docs have descriptive paths (e.g., `quarkiverse/quarkus-amazon-services/...`) that the existing regex patterns can handle.

### `ParsedDocument` Cache Invalidation

The `DocumentService` caches `ParsedDocument` objects in a `ConcurrentHashMap`. These cached objects contain the `subject` field derived at parse time. When Feature 76 changes subject classification logic, cached documents will have stale subjects until the cache is invalidated.

**Mitigation:** The existing `invalidateDocumentCache(version)` method already clears the cache during `CacheRefreshJob`. No additional changes are needed because:
- On first request after startup, there is no cache → fresh subjects are derived
- On cache refresh, the cache is explicitly invalidated
- Subject derivation happens at parse time, not at cache read time

### Debug Logging

Add `log.debug` statements with the classification source (categories, topics, regex, default) to help developers verify correct classification:

```
DEBUG SubjectDeriver - Path 'security-oidc.adoc' classified by categories [security, web] -> 'security'
DEBUG SubjectDeriver - Path 'virtual-threads.adoc' classified by categories [web] -> 'rest-apis'
DEBUG SubjectDeriver - Path 'quarkiverse/amazon-s3.adoc' classified by regex -> 'cloud'
DEBUG SubjectDeriver - Path 'some-random-doc.adoc' no match -> 'misc'
```

---

## Tasks

- [ ] Define `CATEGORY_TO_SUBJECT` static mapping in `SubjectDeriver` (16 entries from R1)
- [ ] Define `TOPIC_KEYWORDS_TO_SUBJECT` static mapping in `SubjectDeriver` (~50 entries from R2)
- [ ] Implement `mapCategoryToSubject(List<String> categories)` — iterate categories, first match wins
- [ ] Implement `mapTopicsToSubject(List<String> topics)` — majority vote algorithm with `LinkedHashMap` for deterministic tie-breaking
- [ ] Add `deriveSubject(String filePath, DocumentMetadata metadata)` overload to `SubjectDeriver`
- [ ] Add `deriveSubjects(List<String>, Map<String, DocumentMetadata>)` batch overload
- [ ] Create `MetadataAwareSubjectResolver` service (R7)
- [ ] Update `DocumentService` — inject `MetadataAwareSubjectResolver`, update 3 call sites (R5.1)
- [ ] Update `SearchService` — inject `MetadataAwareSubjectResolver`, update 2 call sites (R5.2)
- [ ] Update `RelatedDocumentService` — inject `MetadataAwareSubjectResolver`, update 1 call site (R5.3)
- [ ] Update `CodeSampleService` — inject `MetadataAwareSubjectResolver`, update 1 call site (R5.4)
- [ ] Update `QuickSearchService` — inject `MetadataAwareSubjectResolver`, update 1 call site (R5.5)
- [ ] Update `CatalogService` — inject `MetadataAwareSubjectResolver`, update 1 call site (R5.6)
- [ ] Add unit tests for `mapCategoryToSubject()`:
    - Single category `["security"]` → "security"
    - Multi-category `["security", "web"]` → "security" (first wins)
    - Unknown category `["unknown"]` → null
    - Empty list → null
- [ ] Add unit tests for `mapTopicsToSubject()`:
    - Topics `["rest", "resteasy-reactive"]` → "rest-apis" (2 votes)
    - Topics `["security", "oidc", "rest"]` → "security" (2 votes vs 1)
    - No matching topics → null
    - Tie-breaking: `["rest", "kafka"]` → "rest-apis" (first topic's subject wins on tie)
- [ ] Add unit tests for `deriveSubject(filePath, metadata)`:
    - With categories → uses categories
    - Without categories, with topics → uses topics
    - Without metadata → falls back to path-regex
    - With null metadata → falls back to path-regex
    - Exact override still takes priority over metadata
- [ ] Add unit tests for `MetadataAwareSubjectResolver`:
    - `resolveSubject(version, path)` delegates to `SubjectDeriver.deriveSubject(path, metadata)`
    - `resolveSubject(path, metadataMap)` uses pre-loaded map
    - Handles null metadata gracefully
- [ ] Update existing test mocks in `SearchServiceTest`, `RelatedDocumentServiceTest`, `CodeSampleServiceTest`, `QuickSearchServiceTest`, `CatalogServiceTest`, `DocumentServiceTest`, `DocumentServiceBatchTest` to use `MetadataAwareSubjectResolver` instead of `SubjectDeriver`
- [ ] Add integration test verifying catalog doc counts improve after re-indexing with metadata
- [ ] Verify all existing `SubjectDeriver` tests still pass (backward compatibility)
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `deriveSubject("security-oidc.adoc", metadata{categories=["security","web"]})` returns `"security"`
2. `deriveSubject("virtual-threads.adoc", metadata{categories=["web"]})` returns `"rest-apis"`
3. `deriveSubject("hibernate-orm.adoc", metadata{categories=["data"]})` returns `"data-persistence"`
4. `deriveSubject("some-doc.adoc", metadata{topics=["rest","resteasy-reactive"]})` returns `"rest-apis"` (when no categories)
5. `deriveSubject("some-doc.adoc", metadata{topics=["security","oidc","jwt"]})` returns `"security"` (majority vote)
6. `deriveSubject("some-doc.adoc", metadata{topics=["rest","kafka"]})` returns `"rest-apis"` (deterministic tie-breaking — first topic's subject wins)
7. `deriveSubject("quarkiverse-doc.adoc", null)` falls back to path-regex (backward compatible)
8. `deriveSubject("quarkiverse-doc.adoc", DocumentMetadata.empty())` falls back to path-regex (empty metadata treated as absent)
9. Exact path overrides still take highest priority even when metadata is present
10. Existing `deriveSubject(String filePath)` (single-arg) behavior is unchanged — no regression
11. `GET /api/documents?path=virtual-threads.adoc` returns `"subject": "rest-apis"` instead of `"misc"`
12. `GET /api/catalog` shows "misc" with significantly fewer documents (target: < 100, down from 2,548)
13. `GET /api/search/files?subject=security` uses metadata-based classification for filtering (not just path-regex)
14. `GET /api/search/code-samples?subject=security` uses metadata-based classification for filtering
15. `GET /api/quick-search?keywords=oidc&subject=security` uses metadata-based classification for filtering
16. `GET /api/related?path=security-oidc.adoc&subject=security` uses metadata-based classification for filtering
17. `GET /api/code-samples?keywords=oidc` returns results with metadata-derived subjects
18. All existing tests pass unchanged
19. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Category vocabulary changes: Quarkus team adds new categories | Low | Low | Unmapped categories fall through to topics/regex; monitor Quarkus docs repo for category updates |
| Multi-category docs classified incorrectly (first-category-wins heuristic) | Medium | Low | First category is the primary one per Quarkus convention; can refine to use all categories in future |
| Topic majority vote ties (e.g., 1 vote rest, 1 vote security) | Medium | Low | Deterministic tie-breaking via `LinkedHashMap`: first-encountered subject wins; matches author intent (most relevant topic listed first) |
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
| Define category and topic mapping constants | 1.0 |
| Implement `mapCategoryToSubject()` | 0.5 |
| Implement `mapTopicsToSubject()` with deterministic tie-breaking | 1.0 |
| Add `deriveSubject(filePath, metadata)` overload | 1.0 |
| Create `MetadataAwareSubjectResolver` service | 1.0 |
| Integrate into `DocumentService` (3 call sites) | 1.5 |
| Integrate into `SearchService` (2 call sites) | 1.5 |
| Integrate into `RelatedDocumentService` (1 call site) | 0.5 |
| Integrate into `CodeSampleService` (1 call site) | 0.5 |
| Integrate into `QuickSearchService` (1 call site) | 0.5 |
| Integrate into `CatalogService` (1 call site) | 0.5 |
| Unit tests for mapping methods | 1.5 |
| Unit tests for `deriveSubject` with metadata | 1.5 |
| Unit tests for `MetadataAwareSubjectResolver` | 1.0 |
| Update existing test mocks (7 test files) | 2.0 |
| Integration test for catalog accuracy | 1.0 |
| Verify existing tests pass | 0.5 |
| **Total** | **~16.5 hours** |

---

## Files Modified

### New Production Files (1 file)
- `src/main/java/com/fvd/subject/services/MetadataAwareSubjectResolver.java` — centralizes metadata loading + subject derivation for all services (R7)

### Modified Production Files (7 files)
- `src/main/java/com/fvd/subject/services/SubjectDeriver.java` — add `CATEGORY_TO_SUBJECT` mapping, `TOPIC_KEYWORDS_TO_SUBJECT` mapping, `mapCategoryToSubject()`, `mapTopicsToSubject()` (with `LinkedHashMap` for deterministic tie-breaking), `deriveSubject(String, DocumentMetadata)` overload, `deriveSubjects(List, Map)` overload
- `src/main/java/com/fvd/api/services/DocumentService.java` — replace `SubjectDeriver` usage with `MetadataAwareSubjectResolver` in `getOrParseDocument()` (line 226), `getDocumentByPathBrief()` (line 135), `searchDocuments()` (line 162)
- `src/main/java/com/fvd/search/services/SearchService.java` — replace `SubjectDeriver` usage with `MetadataAwareSubjectResolver` in `getFileResults()` (line 79), `searchCodeSamples()` (line 312)
- `src/main/java/com/fvd/api/services/RelatedDocumentService.java` — replace `SubjectDeriver` usage with `MetadataAwareSubjectResolver` in `findRelatedDocuments()` (line 90)
- `src/main/java/com/fvd/api/services/CodeSampleService.java` — replace `SubjectDeriver` usage with `MetadataAwareSubjectResolver` in `searchCodeSamples()` (line 56)
- `src/main/java/com/fvd/api/services/QuickSearchService.java` — replace `SubjectDeriver` usage with `MetadataAwareSubjectResolver` in `search()` (line 57)
- `src/main/java/com/fvd/api/services/CatalogService.java` — inject `MetadataAwareSubjectResolver`, use in `buildSubjectList()` (line 93); retain `SubjectDeriver` injection for `resetDocCounts()`, `recordDocument()`, `getAllSubjects()`

### New Test Files (3 files)
- `src/test/java/com/fvd/subject/services/SubjectDeriverMetadataTest.java` — unit tests for metadata-based classification (category mapping, topic mapping, priority chain, tie-breaking, edge cases)
- `src/test/java/com/fvd/subject/services/SubjectDeriverMetadataIntegrationTest.java` — integration test verifying end-to-end classification with real-like metadata
- `src/test/java/com/fvd/subject/services/MetadataAwareSubjectResolverTest.java` — unit tests for the resolver service

### Modified Test Files (7 files)
- `src/test/java/com/fvd/subject/services/SubjectDeriverTest.java` — verify no regressions from adding new overloads
- `src/test/java/com/fvd/api/services/DocumentServiceTest.java` — update mocks to use `MetadataAwareSubjectResolver`
- `src/test/java/com/fvd/api/services/DocumentServiceBatchTest.java` — update mocks to use `MetadataAwareSubjectResolver`
- `src/test/java/com/fvd/search/services/SearchServiceTest.java` — update mocks to use `MetadataAwareSubjectResolver`
- `src/test/java/com/fvd/api/services/RelatedDocumentServiceTest.java` — update mocks to use `MetadataAwareSubjectResolver`
- `src/test/java/com/fvd/api/services/CatalogServiceTest.java` — update mocks to use `MetadataAwareSubjectResolver`
- `src/test/java/com/fvd/api/services/QuickSearchServiceTest.java` — update mocks (if exists; create if needed)

---

## Dependencies

- **Feature 75** (Parse & Index Document Metadata) — `DocumentMetadata` model and `DocumentMetadataStore` must be available
- `SubjectConfig` — no changes needed (existing config structure supports this feature)
- `CacheRefreshJob` — no changes needed (existing cache refresh rebuilds indexes which now include metadata)

---

END OF FILE
