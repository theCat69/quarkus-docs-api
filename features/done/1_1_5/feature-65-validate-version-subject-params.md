# Feature 65: Validate Version and Subject Parameters

> **Dependencies**: Feature 57 (Fix totalCount Mismatch) should ideally be completed first, since it moves subject filtering into `SearchService`. If Feature 57 is not done, subject validation can still be added in `SearchParams.fromRaw()` or the resource endpoints independently.

## Summary

Invalid `version` and `subject` query parameter values silently return empty or misleading results instead of returning 400 with actionable error messages. A request with `version=nonexistent` returns 200 with empty data. A request with `subject=nonexistent-subject` returns `totalCount` showing matches but `returnedCount=0` with no error. This feature adds existence validation for both parameters, returning 400 with the list of valid values when an invalid value is provided.

## User Story

As an **AI agent consuming the Quarkus Docs API**, I want invalid `version` or `subject` values to return a 400 error listing the valid options so that I can self-correct my request without guessing or interpreting ambiguous empty results.

## Motivation

### Current Behavior (version)

When calling `GET /api/search?keywords=security&version=nonexistent`:

1. `InputValidator.resolveVersion("nonexistent")` validates format (regex `[a-zA-Z0-9._/-]+`) — passes
2. `SearchService.searchFiles()` reads the keyword index for version `nonexistent` — no index exists
3. Response: `200 OK` with `totalCount: 0, returnedCount: 0, results: []`

The client cannot distinguish "this version has no security docs" from "this version does not exist." The current `validateVersion()` at `InputValidator.java:29-37` only checks format (regex + no `..`), not existence.

### Current Behavior (subject)

When calling `GET /api/search?keywords=security&subject=nonexistent-subject`:

1. `SearchParams.fromRaw()` calls `normalizeFilter("nonexistent-subject")` — returns `"nonexistent-subject"` (no validation)
2. `SearchService` or the API service applies `FilterUtils.matchesFilter("nonexistent-subject", derivedSubject)` — no documents match
3. Response: `200 OK` with `totalCount: 150` (pre-filter count from Feature 57's bug) and `returnedCount: 0`

The client sees 150 total results but 0 returned, with no indication that `nonexistent-subject` is not a valid subject name.

### Expected Behavior

- `version=nonexistent` → `400 Bad Request: "Unknown version 'nonexistent'. Available versions: main, 3.27, 3.21. Use 'main' for the latest."`
- `subject=nonexistent-subject` → `400 Bad Request: "Unknown subject 'nonexistent-subject'. Available subjects: getting-started, core-concepts, rest-apis, data-persistence, security, messaging, cloud, observability, testing, tooling, extensions, misc"`

### Special Case: `main` Version

The `main` version is the default and should always be accepted, even if it has not been cached yet (e.g., on first startup before the cache warmup completes). This is because `main` is the fallback value when no `version` parameter is provided.

---

## Requirements

### R1: Add Version Existence Validation to `InputValidator`

**File:** `src/main/java/com/fvd/common/validators/InputValidator.java`

`InputValidator` is a `@UtilityClass` (static methods only), so it cannot hold a `CacheService` reference. Two approaches:

**Option A (Recommended): Add a `validateVersionExists()` method that takes the version list as a parameter:**

```java
public static void validateVersionExists(String version, List<String> cachedVersions) {
    if (DEFAULT_VERSION.equals(version)) {
        return; // main is always accepted
    }
    if (!cachedVersions.contains(version)) {
        String available = String.join(", ", cachedVersions);
        if (!cachedVersions.contains(DEFAULT_VERSION)) {
            available = DEFAULT_VERSION + ", " + available;
        }
        throw new InvalidInputException(
                "Unknown version '" + version + "'. Available versions: " + available);
    }
}
```

**Option B: Move validation to `SearchParams.fromRaw()` which would need a `CacheService` parameter.**

Option A is preferred because `InputValidator` is the established location for all parameter validation, and passing the version list keeps the method stateless and testable.

### R2: Add Subject Existence Validation to `InputValidator`

**File:** `src/main/java/com/fvd/common/validators/InputValidator.java`

```java
public static void validateSubjectExists(String subject, Set<String> validSubjects) {
    if (subject == null || subject.isBlank()) {
        return; // null/blank means no filter, always valid
    }
    if (!validSubjects.contains(subject)) {
        String available = String.join(", ", validSubjects.stream().sorted().toList());
        throw new InvalidInputException(
                "Unknown subject '" + subject + "'. Available subjects: " + available);
    }
}
```

The valid subject names come from `SubjectDeriver.getAllSubjects()` (returns `List<Subject>`), mapped to their `name()` field. The `cachedMetadataMap` in `SubjectDeriver` (line 46) contains all defined subjects including defaults.

### R3: Expose Valid Subject Names from `SubjectDeriver`

**File:** `src/main/java/com/fvd/subject/services/SubjectDeriver.java`

Add a convenience method to return the set of valid subject names:

```java
public Set<String> getValidSubjectNames() {
    return Set.copyOf(cachedMetadataMap.keySet());
}
```

This uses the already-cached `cachedMetadataMap` (built at `@PostConstruct`, line 52), so it's a zero-cost lookup. The map is populated from `buildMetadataMap()` (lines 275-293) which includes both default metadata (12 subjects: getting-started, core-concepts, rest-apis, data-persistence, security, messaging, cloud, observability, testing, tooling, extensions, misc) and any configured definitions.

### R4: Call Validation in Resource Endpoints or `SearchParams.fromRaw()`

Since `SearchParams.fromRaw()` is a static factory method on a record (line 22-33 of `SearchParams.java`), it cannot access CDI beans. Two options:

**Option A: Validate in each resource endpoint** (SearchResource, DocumentResource, CatalogResource, CodeSampleResource) after resolving the version.

**Option B (Recommended): Add a non-static `SearchParamsFactory` or extend `SearchParams.fromRaw()` with additional parameters:**

```java
public static SearchParams fromRaw(
        String version, String keywords, String subject,
        String extension, Integer limit, Integer offset,
        List<String> cachedVersions, Set<String> validSubjects) {
    String resolvedVersion = InputValidator.resolveVersion(version);
    InputValidator.validateVersionExists(resolvedVersion, cachedVersions);
    List<String> parsedKeywords = InputValidator.parseKeywords(keywords);
    String normalizedSubject = normalizeFilter(subject);
    InputValidator.validateSubjectExists(normalizedSubject, validSubjects);
    // ... rest of builder
}
```

However, this adds parameters to a heavily-used factory method. **A pragmatic approach**: validate at the resource endpoint level, before calling `SearchParams.fromRaw()`. This keeps `SearchParams` simple and puts validation close to the HTTP layer where error responses are produced.

### R5: Apply Validation in Resource Endpoints

**Files:**
- `src/main/java/com/fvd/api/resources/SearchResource.java` — inject `CacheService` and `SubjectDeriver`, validate before calling `SearchParams.fromRaw()`
- `src/main/java/com/fvd/api/resources/DocumentResource.java` — same, for both path mode (version only) and search mode (version + subject)
- `src/main/java/com/fvd/api/resources/CatalogResource.java` — validate version only (no subject filter on catalog)
- `src/main/java/com/fvd/api/resources/CodeSampleResource.java` — validate version and subject (if subject parameter exists)

For each resource:

```java
// After resolving version, before business logic:
String resolvedVersion = InputValidator.resolveVersion(version);
InputValidator.validateVersionExists(resolvedVersion, cacheService.listCachedVersions());

// After normalizing subject, before business logic:
String normalizedSubject = normalizeFilter(subject);
InputValidator.validateSubjectExists(normalizedSubject, subjectDeriver.getValidSubjectNames());
```

### R6: `CacheService.listCachedVersions()` Already Exists

**File:** `src/main/java/com/fvd/cache/services/CacheService.java`

The method `listCachedVersions()` at lines 44-56 already returns `List<String>` of cached version directory names. No changes needed to `CacheService`.

---

## Implementation Notes

### Error Message Format

The error messages include the list of valid values to enable AI agents to self-correct:

```json
{
    "status": 400,
    "message": "Unknown version '3.99'. Available versions: main, 3.27, 3.21"
}
```

This follows the existing `ErrorResponse` format with `status` and `message` fields, mapped via the existing `InvalidInputException` → `ExceptionMapper` → 400 response chain.

### `main` Always Accepted

The `main` version bypass in `validateVersionExists()` ensures that:
1. Requests with `version=main` (or no version) never fail validation
2. On first startup, before cache warmup completes, the API still accepts `main`
3. If `main` is already in `listCachedVersions()`, the bypass is harmless (just short-circuits)

### No Changes to `CacheService` or Exception Handling

- `CacheService.listCachedVersions()` already exists and is sufficient
- `InvalidInputException` is already mapped to 400 via existing `ExceptionMapper`
- No new exception types needed

---

## Tasks

- [ ] Add `validateVersionExists(String version, List<String> cachedVersions)` to `InputValidator`
- [ ] Add `validateSubjectExists(String subject, Set<String> validSubjects)` to `InputValidator`
- [ ] Add `getValidSubjectNames()` method to `SubjectDeriver`
- [ ] Inject `CacheService` into `SearchResource` and add version existence validation
- [ ] Inject `CacheService` and `SubjectDeriver` into `DocumentResource` and add version + subject validation
- [ ] Inject `CacheService` into `CatalogResource` and add version existence validation
- [ ] Inject `CacheService` and `SubjectDeriver` into `CodeSampleResource` (if it exists) and add validation
- [ ] Add unit tests for `InputValidator.validateVersionExists()`:
    - `main` always passes even when not in cached list
    - Known cached version passes
    - Unknown version throws `InvalidInputException` with message listing available versions
    - Empty cached versions list — `main` still passes, other versions fail
- [ ] Add unit tests for `InputValidator.validateSubjectExists()`:
    - `null` subject passes (no filter)
    - Blank subject passes (no filter)
    - Known subject name passes
    - Unknown subject throws `InvalidInputException` with message listing available subjects
- [ ] Add unit test for `SubjectDeriver.getValidSubjectNames()` — returns expected set of default subjects
- [ ] Add integration tests:
    - `GET /api/search?keywords=security&version=nonexistent` returns 400 with available versions in message
    - `GET /api/search?keywords=security&subject=nonexistent` returns 400 with available subjects in message
    - `GET /api/search?keywords=security&version=main` returns 200 (main always accepted)
    - `GET /api/search?keywords=security&subject=security` returns 200 (valid subject)
    - `GET /api/catalog?version=nonexistent` returns 400 with available versions
    - `GET /api/documents?keywords=security&version=nonexistent` returns 400
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /api/search?keywords=security&version=nonexistent` returns 400 with an error message listing available versions
2. `GET /api/search?keywords=security&subject=nonexistent` returns 400 with an error message listing available subject names
3. `GET /api/search?keywords=security&version=main` returns 200 even if `main` is not yet in the cached versions list
4. `GET /api/search?keywords=security` (no version) defaults to `main` and returns 200
5. `GET /api/search?keywords=security&subject=security` returns 200 (valid subject)
6. `GET /api/catalog?version=nonexistent` returns 400 with available versions
7. `GET /api/documents?path=security-overview.adoc&version=nonexistent` returns 400 with available versions
8. Error messages include the full list of valid values for self-correction
9. `null`/blank `subject` is accepted as "no filter" (no validation error)
10. `InputValidator` remains a `@UtilityClass` — validation methods are static with list/set parameters
11. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| `listCachedVersions()` is slow due to filesystem listing | Low | Low | Already implemented with `Files.list()` which is fast for small directories; cache directory typically has 2-5 version subdirectories |
| Breaking existing clients that send `version=latest` or other conventions | Low | Medium | Only values that pass format validation (`[a-zA-Z0-9._/-]+`) but fail existence check are affected; document the change in release notes |
| `main` bypass allows requests before cache is ready, returning empty results | Medium | Low | This matches current behavior — the difference is that *other* invalid versions now get 400 instead of silent empty results |
| Adding `CacheService` dependency to resource classes increases coupling | Low | Low | Resources already depend on service classes; `CacheService` is a lightweight CDI bean |
| Subject names change between configurations (custom `SubjectConfig`) | Low | Low | `getValidSubjectNames()` reads from `cachedMetadataMap` which is built from both defaults and config at startup |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Add `validateVersionExists()` and `validateSubjectExists()` to `InputValidator` | 0.5 |
| Add `getValidSubjectNames()` to `SubjectDeriver` | 0.25 |
| Inject validators into 3-4 resource endpoints | 1.0 |
| Unit tests for `InputValidator` new methods | 0.75 |
| Unit test for `SubjectDeriver.getValidSubjectNames()` | 0.25 |
| Integration tests for 400 responses on invalid values | 1.0 |
| Run full test suite and fix regressions | 0.5 |
| **Total** | **~4.25 hours** |

---

## Files Modified

### Production Code (4-5 files)
- `src/main/java/com/fvd/common/validators/InputValidator.java` — add `validateVersionExists()` and `validateSubjectExists()` methods
- `src/main/java/com/fvd/subject/services/SubjectDeriver.java` — add `getValidSubjectNames()` method
- `src/main/java/com/fvd/api/resources/SearchResource.java` — inject `CacheService` and `SubjectDeriver`, validate version and subject
- `src/main/java/com/fvd/api/resources/DocumentResource.java` — inject `CacheService` and `SubjectDeriver`, validate version and subject
- `src/main/java/com/fvd/api/resources/CatalogResource.java` — inject `CacheService`, validate version

### Test Code (estimated 3-4 files)
- `src/test/java/com/fvd/common/validators/InputValidatorTest.java` — add tests for new validation methods
- `src/test/java/com/fvd/subject/services/SubjectDeriverTest.java` — add test for `getValidSubjectNames()`
- `src/test/java/com/fvd/api/resources/SearchResourceTest.java` — add integration tests for 400 responses
- `src/test/java/com/fvd/api/resources/DocumentResourceTest.java` — add integration tests for 400 responses

### Unchanged Files
- `src/main/java/com/fvd/cache/services/CacheService.java` — `listCachedVersions()` already exists, no changes needed
- `src/main/java/com/fvd/common/exceptions/InvalidInputException.java` — already maps to 400, no changes needed
- `src/main/java/com/fvd/api/dto/SearchParams.java` — no changes (validation done at resource level)

---

END OF FILE
