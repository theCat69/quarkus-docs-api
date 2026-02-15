# Feature 81: Omit Null Fields in Brief Mode

> **Dependencies**: None. This is a self-contained enhancement. Compatible with Feature 74 (`fields` parameter via `@JsonFilter("fieldSelector")`) — both mechanisms coexist. Compatible with Feature 78 (brief default change) and Feature 79 (batch brief mode).

## Summary

When `brief=true` is used on document endpoints, the response currently includes `"sections": null` and `"codeBlocks": null`, wasting tokens in AI agent context windows. This feature applies `@JsonInclude(JsonInclude.Include.NON_NULL)` to `DocumentResponse` so that null fields are omitted entirely from the serialized JSON output. This reduces per-document payload by ~30 bytes and, more importantly, eliminates unnecessary null tokens that AI agents must parse and discard.

## User Story

As an **AI agent consuming the API through an MCP server**, I want null fields to be omitted from document responses so that my context window is not wasted on tokens like `"sections": null, "codeBlocks": null` that carry no information, and I can parse responses more efficiently.

## Motivation

### Current Behavior (Null Fields Included)

`GET /api/documents?keywords=security&brief=true` returns:

```json
{
    "results": [
        {
            "title": "Security Overview",
            "description": "Quarkus provides comprehensive security features...",
            "path": "security-overview.adoc",
            "subject": "security",
            "extension": "quarkus-core",
            "sections": null,
            "codeBlocks": null,
            "matchedKeywords": ["security"],
            "score": 15.2
        }
    ],
    "totalCount": 42,
    "returnedCount": 20
}
```

The `"sections": null` and `"codeBlocks": null` fields consume ~30 bytes per document and ~8 tokens per document. For 20 results, this wastes ~160 tokens.

Additionally, `"score": null` and `"extension": null` may appear on documents retrieved by path (not via search), adding more unnecessary nulls.

### Desired Behavior (Null Fields Omitted)

`GET /api/documents?keywords=security&brief=true` returns:

```json
{
    "results": [
        {
            "title": "Security Overview",
            "description": "Quarkus provides comprehensive security features...",
            "path": "security-overview.adoc",
            "subject": "security",
            "extension": "quarkus-core",
            "matchedKeywords": ["security"],
            "score": 15.2
        }
    ],
    "totalCount": 42,
    "returnedCount": 20
}
```

The `sections` and `codeBlocks` fields are absent from the JSON — not `null`, but completely omitted. This is standard REST API practice and is semantically equivalent to "field not present in this response mode."

### Path Mode Example

`GET /api/documents?path=security-overview.adoc` returns a single document with full content. All fields have values, so `@JsonInclude(NON_NULL)` has no effect:

```json
{
    "title": "Security Overview",
    "description": "...",
    "path": "security-overview.adoc",
    "subject": "security",
    "extension": "quarkus-core",
    "sections": [ "..." ],
    "codeBlocks": [ "..." ],
    "matchedKeywords": [],
    "score": null
}
```

Wait — `score` is `null` in path mode (no search scoring). With `@JsonInclude(NON_NULL)`, this would be omitted too:

```json
{
    "title": "Security Overview",
    "description": "...",
    "path": "security-overview.adoc",
    "subject": "security",
    "extension": "quarkus-core",
    "sections": [ "..." ],
    "codeBlocks": [ "..." ],
    "matchedKeywords": []
}
```

This is cleaner and more consistent.

### Token Savings

| Scenario | Null Fields | Tokens Saved Per Document | For 20 Results |
|----------|------------|--------------------------|----------------|
| Brief mode | `sections`, `codeBlocks` (2 fields) | ~8 tokens | ~160 tokens |
| Path mode | `score` (1 field) | ~4 tokens | N/A (single doc) |
| Brief + no extension | `sections`, `codeBlocks`, `extension` (3 fields) | ~12 tokens | ~240 tokens |

---

## Scope / Requirements

### R1: Add `@JsonInclude(NON_NULL)` to `DocumentResponse`

**File:** `src/main/java/com/fvd/api/dto/DocumentResponse.java`

Add the `@JsonInclude` annotation at the class level:

```java
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonFilter("fieldSelector")
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {
    public String title;
    public String description;
    public String path;
    public String subject;
    public String extension;
    public List<SectionInfo> sections;
    public List<CodeBlockInfo> codeBlocks;
    public List<String> matchedKeywords;
    public Double score;
}
```

### R2: Consider Global vs. Selective Application

**Decision: Apply selectively, not globally.**

Applying `@JsonInclude(NON_NULL)` globally (via `ObjectMapperCustomizer`) would affect all DTOs in the application. While this is generally desirable, it has broader implications:

- `ProblemDetail` error responses currently include all fields — some may be intentionally null
- `BatchDocumentResponse.errors` could be an empty list vs. null — different semantics
- `CatalogResponse` fields should always be present (even if empty list)

**Selective application to `DocumentResponse` only** is safer and addresses the primary token waste. Other DTOs can be migrated in future features if needed.

However, consider also applying to these DTOs where null fields are common:

- `DocumentSearchResponse` — the `warning` field (added in Feature 78) should be omitted when null
- `SearchResultRef` — `snippet` may be null for certain results
- `CodeSampleResult` — `matchedSectionTitle` and `sectionMatchScore` may be null

### R3: Ensure Compatibility with `@JsonFilter("fieldSelector")`

**Analysis:** `@JsonInclude` and `@JsonFilter` are independent Jackson annotations that coexist correctly:

1. `@JsonFilter("fieldSelector")` — controls **which fields are serialized** based on the `fields` query parameter. When no `fields` param is present, all fields are serialized (via `serializeAll()` default filter).
2. `@JsonInclude(NON_NULL)` — controls **whether a field with a null value is serialized**. It runs during serialization regardless of the filter.

**Processing order:**
1. Jackson checks the `@JsonFilter` — if the field is excluded by the filter, it is not serialized (regardless of `@JsonInclude`)
2. If the field passes the filter, Jackson checks `@JsonInclude` — if the value is null and the inclusion is `NON_NULL`, the field is not serialized

**No conflict.** The two annotations are complementary:
- `fields=title,sections` + `@JsonInclude(NON_NULL)` + `brief=true`: Only `title` is in the output (sections is filtered by `fields` if not explicitly requested, or omitted by `NON_NULL` if null)
- `fields=title,sections` + `@JsonInclude(NON_NULL)` + `brief=false`: Both `title` and `sections` appear (sections has a value)

### R4: Handle `matchedKeywords` Empty List vs. Null

**Current behavior:** `matchedKeywords` is set to `List.of()` (empty list) in path mode and populated in search mode. With `@JsonInclude(NON_NULL)`, an empty list is **not null** and will still be serialized as `"matchedKeywords": []`.

This is correct — an empty list means "no keywords matched" (for path mode), which is different from `null` (field not applicable). If we wanted to omit empty lists too, we would use `@JsonInclude(NON_EMPTY)`, but that would also omit empty strings, which could hide valid data.

**Decision:** Use `NON_NULL`, not `NON_EMPTY`. Empty lists and empty strings are preserved.

### R5: Apply to Additional DTOs (Optional, Recommended)

Apply `@JsonInclude(NON_NULL)` to DTOs where null fields are common in practice:

**File:** `src/main/java/com/fvd/api/dto/DocumentSearchResponse.java`

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuperBuilder
@NoArgsConstructor
@RegisterForReflection
public class DocumentSearchResponse extends PaginatedResponse<DocumentResponse> {
    public String warning;
}
```

This ensures the `warning` field (from Feature 78) is omitted when null, instead of appearing as `"warning": null`.

---

## Technical Design

### Jackson `@JsonInclude` Semantics

`@JsonInclude(JsonInclude.Include.NON_NULL)` at the class level applies to all properties of that class. Jackson checks each property's value during serialization:

- If the value is `null`, the property is **not written** to the JSON output
- If the value is non-null (including empty strings, empty lists, zero, false), the property **is written**

This is a serialization-only concern — deserialization (JSON → Java) is unaffected. A JSON input without `sections` will still deserialize to `sections = null` in the Java object.

### Annotation Placement

The annotation is placed at the **class level**, not on individual fields. This is cleaner and ensures all null fields are omitted consistently. If specific fields should always be serialized (even when null), they can be annotated with `@JsonInclude(JsonInclude.Include.ALWAYS)` to override the class-level setting.

### No Impact on Tests Using JSON Matchers

RestAssured tests that use `body("sections", nullValue())` will need to be updated. With `NON_NULL`, the `sections` field is absent from the response, so `body("sections", nullValue())` may still work (RestAssured treats absent JSON paths as null) or may need to change to `body("$", not(hasKey("sections")))`.

**Verification needed:** Test how RestAssured handles absent JSON fields vs. `null` JSON fields. In Hamcrest/JsonPath:
- `body("sections", nullValue())` — may still pass for absent fields (JsonPath returns null for missing paths)
- `body("$", not(hasKey("sections")))` — explicitly asserts the key is absent

The implementer should verify RestAssured behavior and update tests accordingly.

---

## Request/Response Examples

### Example 1: Brief mode — null fields omitted

**Request:**
```
GET /api/documents?keywords=security&brief=true
```

**Response (200):**
```json
{
    "results": [
        {
            "title": "Security Overview",
            "description": "Quarkus provides comprehensive security features...",
            "path": "security-overview.adoc",
            "subject": "security",
            "extension": "quarkus-core",
            "matchedKeywords": ["security"],
            "score": 15.2
        }
    ],
    "totalCount": 42,
    "returnedCount": 20
}
```

Note: `sections` and `codeBlocks` are **absent** (not `null`).

### Example 2: Full mode — all fields present

**Request:**
```
GET /api/documents?keywords=security&brief=false&limit=1
```

**Response (200):**
```json
{
    "results": [
        {
            "title": "Security Overview",
            "description": "Quarkus provides comprehensive security features...",
            "path": "security-overview.adoc",
            "subject": "security",
            "extension": "quarkus-core",
            "sections": [
                {
                    "title": "Authentication",
                    "level": 2,
                    "content": "...",
                    "startLine": 15,
                    "endLine": 120
                }
            ],
            "codeBlocks": [
                {
                    "language": "java",
                    "content": "@Path(\"/hello\")...",
                    "context": "Authentication",
                    "startLine": 25,
                    "endLine": 35
                }
            ],
            "matchedKeywords": ["security"],
            "score": 15.2
        }
    ],
    "totalCount": 42,
    "returnedCount": 1
}
```

All fields are present because `sections` and `codeBlocks` are non-null lists.

### Example 3: Path mode — score omitted

**Request:**
```
GET /api/documents?path=security-overview.adoc
```

**Response (200):**
```json
{
    "title": "Security Overview",
    "description": "Quarkus provides comprehensive security features...",
    "path": "security-overview.adoc",
    "subject": "security",
    "extension": "quarkus-core",
    "sections": [ "..." ],
    "codeBlocks": [ "..." ],
    "matchedKeywords": []
}
```

Note: `score` is absent because it is `null` in path mode (no search scoring). `matchedKeywords` is `[]` (empty list, non-null) so it is still present.

### Example 4: Batch brief mode

**Request:**
```
POST /api/documents/batch
Content-Type: application/json

{
    "paths": ["security-overview.adoc"],
    "version": "main",
    "brief": true
}
```

**Response (200):**
```json
{
    "documents": [
        {
            "title": "Security Overview",
            "description": "...",
            "path": "security-overview.adoc",
            "subject": "security",
            "extension": "quarkus-core",
            "matchedKeywords": []
        }
    ],
    "errors": [],
    "requestedCount": 1,
    "retrievedCount": 1,
    "errorCount": 0
}
```

`sections`, `codeBlocks`, and `score` are all absent.

### Example 5: With `fields` parameter — both filters apply

**Request:**
```
GET /api/documents?keywords=security&brief=true&fields=title,path
```

**Response (200):**
```json
{
    "results": [
        {
            "title": "Security Overview",
            "path": "security-overview.adoc"
        }
    ],
    "totalCount": 42,
    "returnedCount": 20
}
```

The `fields` filter excludes all fields except `title` and `path`. The `@JsonInclude(NON_NULL)` has no additional effect here because the excluded fields are never serialized.

---

## Implementation Notes

### Minimal Code Change

This feature requires only adding one annotation (`@JsonInclude(JsonInclude.Include.NON_NULL)`) to `DocumentResponse.java` and optionally to `DocumentSearchResponse.java`. It is the simplest feature in this batch.

### `NON_NULL` vs. `NON_EMPTY` vs. `NON_ABSENT`

| Strategy | Omits `null` | Omits `""` | Omits `[]` | Omits `0` |
|----------|-------------|-----------|-----------|----------|
| `NON_NULL` | Yes | No | No | No |
| `NON_EMPTY` | Yes | Yes | Yes | No |
| `NON_ABSENT` | Yes | No | No | No |

**`NON_NULL` is the correct choice** because:
- Empty strings may be valid descriptions (unlikely but possible)
- Empty lists (`matchedKeywords: []`) carry semantic meaning ("no keywords matched")
- Zero values (`score: 0.0`) carry semantic meaning
- `NON_ABSENT` is equivalent to `NON_NULL` for standard Java types (it adds Optional handling, which we don't use)

### Impact on OpenAPI Schema

`@JsonInclude(NON_NULL)` does not affect the OpenAPI schema generated by Quarkus/SmallRye OpenAPI. The schema will still show all fields as part of the `DocumentResponse` schema. Fields are described as optional (nullable) rather than required. This is correct — the fields exist in the schema but may be absent from specific responses.

### Impact on Deserialization (Tests)

Tests that create `DocumentResponse` objects manually are unaffected — `@JsonInclude` only affects serialization (Java → JSON), not deserialization (JSON → Java). Tests that read JSON responses from the API and assert field presence/absence will need updates:

```java
// Before (field present with null value):
body("results[0].sections", nullValue())

// After (field absent):
body("results[0]", not(hasKey("sections")))
// OR (JsonPath returns null for absent paths, so this may still work):
body("results[0].sections", nullValue())
```

The implementer should verify RestAssured/JsonPath behavior for absent keys.

---

## Tasks

- [ ] Add `@JsonInclude(JsonInclude.Include.NON_NULL)` to `DocumentResponse`
- [ ] Add `@JsonInclude(JsonInclude.Include.NON_NULL)` to `DocumentSearchResponse` (for `warning` field from Feature 78)
- [ ] Add integration test: brief mode response does not contain `"sections"` or `"codeBlocks"` keys
- [ ] Add integration test: full mode response still contains `sections` and `codeBlocks` with values
- [ ] Add integration test: path mode response does not contain `"score"` key when score is null
- [ ] Add integration test: path mode response still contains `matchedKeywords` as empty list
- [ ] Add integration test: batch brief mode does not contain null fields
- [ ] Add integration test: `@JsonInclude(NON_NULL)` works correctly with `fields` query parameter
- [ ] Update existing tests that assert `sections` or `codeBlocks` is `nullValue()` — verify they still pass or update to `not(hasKey(...))`
- [ ] Verify all existing tests pass
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /api/documents?keywords=security&brief=true` response items do **not** contain `"sections"` or `"codeBlocks"` keys
2. `GET /api/documents?keywords=security&brief=false` response items **do** contain `sections` and `codeBlocks` with non-null values
3. `GET /api/documents?path=security-overview.adoc` response does **not** contain `"score"` key (score is null in path mode)
4. `GET /api/documents?path=security-overview.adoc` response **does** contain `"matchedKeywords": []` (empty list is non-null)
5. `POST /api/documents/batch` with `brief=true` omits null fields from each document
6. `@JsonInclude(NON_NULL)` on `DocumentResponse` does not conflict with `@JsonFilter("fieldSelector")`
7. `GET /api/documents?keywords=security&brief=true&fields=title,path` correctly applies both filters
8. OpenAPI schema still lists all `DocumentResponse` fields (schema is not affected by `@JsonInclude`)
9. All existing tests pass (with minor updates for absent vs. null field assertions)
10. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Existing tests asserting `sections` is `nullValue()` may fail if RestAssured treats absent fields differently from null fields | Medium | Low | Verify RestAssured/JsonPath behavior; update test assertions if needed. JsonPath typically returns `null` for absent paths, so existing assertions may still pass. |
| `@JsonInclude(NON_NULL)` conflicts with `@JsonFilter("fieldSelector")` | Very Low | High | Jackson processes `@JsonFilter` and `@JsonInclude` independently. Filter controls field inclusion; `NON_NULL` controls null value omission. Verified by Jackson documentation. |
| Clients that explicitly check for `"sections": null` (not just absence) will break | Low | Low | Standard JSON practice is to treat absent fields as equivalent to null. Clients using `optionalField != null` checks are not affected. |
| `extension` field is sometimes null (when not found in keyword index) and will now be omitted | Medium | Low | This is desirable — omitting `"extension": null` is cleaner than including it. Document the change. |
| Future DTOs may inadvertently inherit `NON_NULL` if applied globally | N/A | N/A | We chose selective application (only `DocumentResponse` and `DocumentSearchResponse`), so this risk does not apply. |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Add `@JsonInclude(NON_NULL)` to `DocumentResponse` | 0.1 |
| Add `@JsonInclude(NON_NULL)` to `DocumentSearchResponse` | 0.1 |
| Integration tests for null field omission (6 test methods) | 1.5 |
| Update existing tests for absent vs. null assertions | 1.0 |
| Verify `@JsonFilter` compatibility | 0.25 |
| Run full test suite and fix regressions | 0.5 |
| **Total** | **~3.5 hours** |

---

## Files Modified

### Modified Production Files (2 files)
- `src/main/java/com/fvd/api/dto/DocumentResponse.java` — add `@JsonInclude(JsonInclude.Include.NON_NULL)`
- `src/main/java/com/fvd/api/dto/DocumentSearchResponse.java` — add `@JsonInclude(JsonInclude.Include.NON_NULL)` (for `warning` field)

### New Test Files (1 file)
- `src/test/java/com/fvd/api/resources/DocumentResponseNullFieldsTest.java` — integration tests for null field omission in brief mode, path mode, and batch mode

### Modified Test Files (estimated 1-2 files)
- `src/test/java/com/fvd/api/resources/DocumentResourceTest.java` — update assertions from `nullValue()` to `not(hasKey(...))` if needed
- `src/test/java/com/fvd/api/resources/BatchDocumentBriefTest.java` — update if brief assertions check for null fields

### Unchanged Files
- `src/main/java/com/fvd/common/config/FieldSelectionObjectMapperCustomizer.java` — no changes needed (filter and include are independent)
- `src/main/java/com/fvd/common/filters/FieldSelectionFilter.java` — no changes needed
- `src/main/java/com/fvd/api/dto/PaginatedResponse.java` — no `@JsonInclude` needed (all fields are primitives)
- `src/main/java/com/fvd/api/dto/SectionInfo.java` — not applicable (always has values)
- `src/main/java/com/fvd/api/dto/CodeBlockInfo.java` — not applicable (always has values)
- `src/main/java/com/fvd/api/dto/BatchDocumentResponse.java` — not applying `NON_NULL` (errors list should always be present)

---

## Dependencies

- **None** — this feature is independent and can be implemented without any other feature.
- **Complements Feature 78** — when brief defaults to `true`, null fields are automatically omitted.
- **Complements Feature 79** — batch brief mode benefits from null field omission.
- **Compatible with Feature 74** — `@JsonFilter("fieldSelector")` and `@JsonInclude(NON_NULL)` coexist.

---

END OF FILE
