# Feature 76A: Metadata Classification Logic in SubjectDeriver

> **Dependencies**: Feature 75 (Parse & Index Document Metadata). Requires `DocumentMetadata` model to be available.

## Summary

This feature adds metadata-driven classification logic to `SubjectDeriver.java` — the core mapping tables and algorithms that use `:categories:` and `:topics:` document attributes to derive subjects. It introduces a `CATEGORY_TO_SUBJECT` static mapping (16 entries), a `TOPIC_KEYWORDS_TO_SUBJECT` keyword mapping (~57 entries), and new method overloads (`deriveSubject(filePath, metadata)` and `deriveSubjects(filePaths, metadataByPath)`) that integrate metadata into the existing derivation pipeline. No service files are modified; no new service classes are created. Service integration is handled separately in Feature 76B.

## User Story

As an **AI agent consuming the API through an MCP server**, I want the `SubjectDeriver` to be able to classify documents using their `:categories:` and `:topics:` metadata attributes so that the classification logic is accurate and ready for service-level integration, replacing the broken path-regex heuristic that misclassifies 2,548 out of ~2,800 documents as "misc".

## Motivation

### Current Behavior

`SubjectDeriver.deriveSubject("virtual-threads.adoc")` returns `"misc"` because no regex pattern matches "virtual-threads". But the document has `:categories: web` and `:topics: rest,resteasy-reactive,virtual-threads` — it should be classified as "rest-apis".

The path-regex approach cannot capture the semantic meaning of documents. Out of ~2,800 indexed documents, 2,548 are classified as "misc" while categories like "rest-apis" have only 6 documents.

### Desired Behavior

After this feature, calling the new overload `deriveSubject("virtual-threads.adoc", metadata)` where `metadata` has `categories=["web"]` returns `"rest-apis"`. The classification priority chain becomes:

```
1. Exact path overrides (existing, from config)     → highest priority
2. Glob pattern overrides (existing, from config)    → second priority
3. :categories: metadata mapping (NEW)               → primary classification source
4. :topics: metadata analysis (NEW)                  → fallback when categories don't match
5. Regex path patterns (existing)                    → last resort for docs without metadata
6. Default "misc"                                    → absolute fallback
```

The existing `deriveSubject(String filePath)` single-arg method remains unchanged for backward compatibility — callers that have metadata use the new overload. Service-level integration (updating all callers to pass metadata) is covered by Feature 76B.

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
    Map.entry("auth", "security"),
    Map.entry("hibernate", "data-persistence"),
    Map.entry("panache", "data-persistence"),
    Map.entry("jpa", "data-persistence"),
    Map.entry("jdbc", "data-persistence"),
    Map.entry("datasource", "data-persistence"),
    Map.entry("database", "data-persistence"),
    Map.entry("sql", "data-persistence"),
    Map.entry("nosql", "data-persistence"),
    Map.entry("mongodb", "data-persistence"),
    Map.entry("redis", "data-persistence"),
    Map.entry("kafka", "messaging"),
    Map.entry("amqp", "messaging"),
    Map.entry("messaging", "messaging"),
    Map.entry("reactive-messaging", "messaging"),
    Map.entry("rabbitmq", "messaging"),
    Map.entry("pulsar", "messaging"),
    Map.entry("kubernetes", "cloud"),
    Map.entry("openshift", "cloud"),
    Map.entry("docker", "cloud"),
    Map.entry("container", "cloud"),
    Map.entry("aws", "cloud"),
    Map.entry("azure", "cloud"),
    Map.entry("gcp", "cloud"),
    Map.entry("metrics", "observability"),
    Map.entry("health", "observability"),
    Map.entry("tracing", "observability"),
    Map.entry("opentelemetry", "observability"),
    Map.entry("micrometer", "observability"),
    Map.entry("logging", "observability"),
    Map.entry("test", "testing"),
    Map.entry("junit", "testing"),
    Map.entry("mock", "testing"),
    Map.entry("testing", "testing"),
    Map.entry("cdi", "core-concepts"),
    Map.entry("config", "core-concepts"),
    Map.entry("lifecycle", "core-concepts"),
    Map.entry("injection", "core-concepts"),
    Map.entry("bean", "core-concepts"),
    Map.entry("native", "core-concepts"),
    Map.entry("graalvm", "core-concepts"),
    Map.entry("getting-started", "getting-started"),
    Map.entry("quickstart", "getting-started"),
    Map.entry("tutorial", "getting-started"),
    Map.entry("extension", "extensions"),
    Map.entry("quarkiverse", "extensions"),
    Map.entry("cli", "tooling"),
    Map.entry("dev-services", "tooling"),
    Map.entry("devmode", "tooling"),
    Map.entry("ide", "tooling"),
    Map.entry("maven", "tooling"),
    Map.entry("gradle", "tooling"),
    Map.entry("quarkus-cli", "tooling")
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

### Backward Compatibility

The existing `deriveSubject(String filePath)` method is NOT modified. It continues to use path-regex only. This ensures:
- All existing callers work unchanged
- Tests pass without modification
- The new metadata-aware path is opt-in via the new overload

Over time, callers should migrate to `deriveSubject(filePath, metadata)`. Caller migration is handled in Feature 76B.

### No Config Changes Required

The `CATEGORY_TO_SUBJECT` mapping is defined as a static constant in `SubjectDeriver`. Rationale:
- The Quarkus docs category vocabulary is stable (defined by the Quarkus team)
- Making it configurable adds complexity without benefit (categories won't change without a code update anyway)
- If categories change, updating the constant is a one-line change

---

## Request/Response Examples

This feature does not change any API responses. It adds classification logic to `SubjectDeriver` that is not yet wired to any service callers. The following examples illustrate the classification behavior at the method level:

### Category-Based Classification

```java
// Document with :categories: web
DocumentMetadata meta = DocumentMetadata.builder()
    .categories(List.of("web")).build();
subjectDeriver.deriveSubject("virtual-threads.adoc", meta);
// → "rest-apis" (category "web" maps to "rest-apis")

// Document with :categories: security,web (first category wins)
DocumentMetadata meta = DocumentMetadata.builder()
    .categories(List.of("security", "web")).build();
subjectDeriver.deriveSubject("security-oidc.adoc", meta);
// → "security" (first category "security" maps to "security")

// Document with :categories: data
DocumentMetadata meta = DocumentMetadata.builder()
    .categories(List.of("data")).build();
subjectDeriver.deriveSubject("hibernate-orm.adoc", meta);
// → "data-persistence" (category "data" maps to "data-persistence")
```

### Topic-Based Classification (Fallback)

```java
// Document without categories, with topics (majority vote)
DocumentMetadata meta = DocumentMetadata.builder()
    .topics(List.of("security", "oidc", "jwt")).build();
subjectDeriver.deriveSubject("some-doc.adoc", meta);
// → "security" (3 votes for security)

// Tie-breaking: first topic's subject wins
DocumentMetadata meta = DocumentMetadata.builder()
    .topics(List.of("rest", "kafka")).build();
subjectDeriver.deriveSubject("some-doc.adoc", meta);
// → "rest-apis" (1 vote each, but "rest" appears first → rest-apis wins)
```

### Fallback to Path-Regex

```java
// Null metadata → path-regex fallback
subjectDeriver.deriveSubject("quarkiverse-doc.adoc", null);
// → falls through to regex matching, then to "misc"

// Empty metadata → path-regex fallback
subjectDeriver.deriveSubject("quarkiverse-doc.adoc", DocumentMetadata.empty());
// → falls through to regex matching, then to "misc"
```

### Exact Override Still Wins

```java
// Even with metadata, exact path overrides take highest priority
DocumentMetadata meta = DocumentMetadata.builder()
    .categories(List.of("security")).build();
subjectDeriver.deriveSubject("getting-started-guide.adoc", meta);
// → "getting-started" (if exact override exists for this path)
```

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

### Debug Logging

Add `log.trace` statements with the classification source (categories, topics, regex, default) to help developers verify correct classification:

```
TRACE SubjectDeriver - Path 'security-oidc.adoc' classified by categories [security, web] -> 'security'
TRACE SubjectDeriver - Path 'virtual-threads.adoc' classified by categories [web] -> 'rest-apis'
TRACE SubjectDeriver - Path 'quarkiverse/amazon-s3.adoc' classified by regex -> 'cloud'
TRACE SubjectDeriver - Path 'some-random-doc.adoc' no match -> 'misc'
```

---

## Tasks

- [ ] Define `CATEGORY_TO_SUBJECT` static mapping in `SubjectDeriver` (16 entries from R1)
- [ ] Define `TOPIC_KEYWORDS_TO_SUBJECT` static mapping in `SubjectDeriver` (~57 entries from R2)
- [ ] Implement `mapCategoryToSubject(List<String> categories)` — iterate categories, first match wins
- [ ] Implement `mapTopicsToSubject(List<String> topics)` — majority vote algorithm with `LinkedHashMap` for deterministic tie-breaking
- [ ] Add `deriveSubject(String filePath, DocumentMetadata metadata)` overload to `SubjectDeriver`
- [ ] Add `deriveSubjects(List<String>, Map<String, DocumentMetadata>)` batch overload
- [ ] Add unit tests for `mapCategoryToSubject()`:
    - Single category `["security"]` → "security"
    - Multi-category `["security", "web"]` → "security" (first wins)
    - Unknown category `["unknown"]` → null
    - Empty list → null
- [ ] Add unit tests for `mapTopicsToSubject()`:
    - Topics `["rest", "resteasy"]` → "rest-apis" (2 votes)
    - Topics `["security", "oidc", "rest"]` → "security" (2 votes vs 1)
    - No matching topics → null
    - Tie-breaking: `["rest", "kafka"]` → "rest-apis" (first topic's subject wins on tie)
- [ ] Add unit tests for `deriveSubject(filePath, metadata)`:
    - With categories → uses categories
    - Without categories, with topics → uses topics
    - Without metadata → falls back to path-regex
    - With null metadata → falls back to path-regex
    - Exact override still takes priority over metadata
- [ ] Add integration test for end-to-end classification with real-like metadata
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
11. All existing tests pass unchanged
12. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Category vocabulary changes: Quarkus team adds new categories | Low | Low | Unmapped categories fall through to topics/regex; monitor Quarkus docs repo for category updates |
| Multi-category docs classified incorrectly (first-category-wins heuristic) | Medium | Low | First category is the primary one per Quarkus convention; can refine to use all categories in future |
| Topic majority vote ties (e.g., 1 vote rest, 1 vote security) | Medium | Low | Deterministic tie-breaking via `LinkedHashMap`: first-encountered subject wins; matches author intent (most relevant topic listed first) |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Define category and topic mapping constants | 1.0 |
| Implement `mapCategoryToSubject()` | 0.5 |
| Implement `mapTopicsToSubject()` with deterministic tie-breaking | 1.0 |
| Add `deriveSubject(filePath, metadata)` overload and batch overload | 1.0 |
| Unit tests for mapping methods | 1.5 |
| **Total** | **~5.0 hours** |

---

## Files Modified

### Modified Production Files (1 file)
- `src/main/java/com/fvd/subject/services/SubjectDeriver.java` — add `CATEGORY_TO_SUBJECT` mapping, `TOPIC_KEYWORDS_TO_SUBJECT` mapping, `mapCategoryToSubject()`, `mapTopicsToSubject()` (with `LinkedHashMap` for deterministic tie-breaking), `deriveSubject(String, DocumentMetadata)` overload, `deriveSubjects(List, Map)` overload

### New Test Files (2 files)
- `src/test/java/com/fvd/subject/services/SubjectDeriverMetadataTest.java` — unit tests for metadata-based classification (category mapping, topic mapping, priority chain, tie-breaking, edge cases)
- `src/test/java/com/fvd/subject/services/SubjectDeriverMetadataIntegrationTest.java` — integration test verifying end-to-end classification with real-like metadata

### Verified Unchanged Test Files (1 file)
- `src/test/java/com/fvd/subject/services/SubjectDeriverTest.java` — verify no regressions from adding new overloads

---

## Dependencies

- **Feature 75** (Parse & Index Document Metadata) — `DocumentMetadata` model must be available for the new method signatures and tests
- `SubjectConfig` — no changes needed (existing config structure supports this feature)

---

END OF FILE
