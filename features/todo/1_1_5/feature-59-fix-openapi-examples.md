# Feature 59: Fix OpenAPI Example Values

> **Dependencies**: None. This is a documentation-only change with no logic modifications.

## Summary

The `@Parameter` annotations on all 4 REST resource classes use `example = "3.27"` for the `version` parameter, but version `3.27` has no indexed data unless explicitly cached. AI agents (and human developers) using the Swagger UI "Try it out" feature will get empty results when they click "Execute" with the default example values. The fix updates example values to use realistic defaults that return actual results when the API is running with default configuration.

## User Story

As an **AI agent or developer using the Swagger UI**, I want the example values in OpenAPI parameter annotations to produce non-empty results when I "Try it out" so that I can quickly understand the API response format and verify the API is working correctly.

## Motivation

### Current Problem

The `version` parameter across all 4 resources uses `example = "3.27"`:

| Resource | File | Line | Current Example |
|----------|------|------|-----------------|
| `CatalogResource` | `CatalogResource.java` | 53 | `example = "3.27"` |
| `SearchResource` | `SearchResource.java` | 53 | `example = "3.27"` |
| `DocumentResource` | `DocumentResource.java` | 63 | `example = "3.27"` |
| `CodeSampleResource` | `CodeSampleResource.java` | 52 | `example = "3.27"` |

The `version` parameter defaults to `main` (via `@Schema(defaultValue = "main")` and `InputValidator.resolveVersion()`), so omitting it works. But the **example** value shown in Swagger UI is `3.27`, which misleads users into thinking `3.27` is a valid, pre-cached version.

Additionally, `CodeSampleResource` uses `example = "quarkus-resteasy-reactive"` for the `extension` parameter (line 81), which may not exist in the index unless the specific extension has been cached. The extension `quarkus-core` is always present because core docs are always indexed.

### Impact

When using Swagger UI:
1. User clicks "Try it out" on `/api/search`
2. The `version` field is pre-filled with `3.27`
3. User clicks "Execute"
4. Response is empty or returns an error because version `3.27` is not cached by default
5. User thinks the API is broken

---

## Requirements

### R1: Update `version` Example to `main`

**All 4 resource files:** Change `example = "3.27"` to `example = "main"` for the `version` parameter.

This aligns the example with the `defaultValue = "main"` already set in `@Schema`. The `main` version is always cached on startup (configured via `app.versions=main` in `application.properties`).

**Files and lines:**

| File | Line | Before | After |
|------|------|--------|-------|
| `CatalogResource.java` | 53 | `example = "3.27"` | `example = "main"` |
| `SearchResource.java` | 53 | `example = "3.27"` | `example = "main"` |
| `DocumentResource.java` | 63 | `example = "3.27"` | `example = "main"` |
| `CodeSampleResource.java` | 52 | `example = "3.27"` | `example = "main"` |

### R2: Update `extension` Example on `CodeSampleResource`

**File:** `src/main/java/com/fvd/api/resources/CodeSampleResource.java`, line 81

**Before:**
```java
example = "quarkus-resteasy-reactive"
```

**After:**
```java
example = "quarkus-core"
```

`quarkus-core` is always present in the index because core documentation files are always indexed. The extension `quarkus-resteasy-reactive` may not exist unless quarkiverse data is available.

### R3: Verify Other Example Values Are Reasonable

Review all `@Parameter` example values across all 4 resources and confirm they are valid:

| Resource | Parameter | Current Example | Verdict |
|----------|-----------|----------------|---------|
| `SearchResource` | `keywords` | `"security authentication"` | OK -- common keywords |
| `SearchResource` | `subject` | `"security"` | OK -- valid subject |
| `SearchResource` | `extension` | `"quarkus-core"` | OK -- always present |
| `SearchResource` | `limit` | `"20"` | OK -- valid default |
| `SearchResource` | `offset` | `"0"` | OK -- valid default |
| `DocumentResource` | `path` | `"security-overview.adoc"` | OK -- common doc |
| `DocumentResource` | `keywords` | `"security oidc"` | OK -- common keywords |
| `DocumentResource` | `subject` | `"security"` | OK -- valid subject |
| `DocumentResource` | `extension` | `"quarkus-core"` | OK -- always present |
| `CodeSampleResource` | `keywords` | `"rest endpoint"` | OK -- common keywords |
| `CodeSampleResource` | `language` | `"java"` | OK -- most common language |
| `CodeSampleResource` | `subject` | `"rest-apis"` | OK -- valid subject |
| `CodeSampleResource` | `extension` | `"quarkus-resteasy-reactive"` | **CHANGE** to `"quarkus-core"` |

### R4: Update Documentation Guidelines Example

**File:** `.project-guidelines-for-ai/documentation/guidelines.md`, line 21

The endpoint annotation pattern example in the documentation guidelines also uses `example = "3.27"`:

```java
@Parameter(description = "Quarkus version branch or tag. Defaults to 'main' if omitted.",
        required = false, example = "3.27", schema = @Schema(defaultValue = "main"))
```

Update to `example = "main"` to keep the guidelines consistent with the actual code.

---

## Implementation Notes

### Why `main` Instead of a Specific Version

- `main` is the default version configured in `app.versions=main` in `application.properties`
- `main` is always cached at startup, so queries with `version=main` always return data
- Specific versions like `3.27` require explicit configuration (`app.versions=main,3.27`) to be cached
- Using `main` as the example matches the `defaultValue` already set in `@Schema`

### Why `quarkus-core` for Extension Examples

- Core Quarkus documentation files are always indexed under the `quarkus-core` extension
- `quarkus-core` is the most populated extension with the most keyword and code sample entries
- Quarkiverse extensions like `quarkus-resteasy-reactive` are only available if quarkiverse ingestion is enabled and the extension exists in the playbook

### No Logic Changes

This feature only modifies `@Parameter` annotation string values. No runtime behavior, validation logic, or test assertions change.

---

## Tasks

- [ ] Update `CatalogResource.java` line 53: `example = "3.27"` → `example = "main"`
- [ ] Update `SearchResource.java` line 53: `example = "3.27"` → `example = "main"`
- [ ] Update `DocumentResource.java` line 63: `example = "3.27"` → `example = "main"`
- [ ] Update `CodeSampleResource.java` line 52: `example = "3.27"` → `example = "main"`
- [ ] Update `CodeSampleResource.java` line 81: `example = "quarkus-resteasy-reactive"` → `example = "quarkus-core"`
- [ ] Update `.project-guidelines-for-ai/documentation/guidelines.md` line 21: `example = "3.27"` → `example = "main"`
- [ ] Verify Swagger UI renders correct examples at `/q/swagger-ui` in dev mode
- [ ] Run `./gradlew test` -- all tests pass (no test changes needed)

---

## Acceptance Criteria

1. All 4 resource files use `example = "main"` for the `version` parameter
2. `CodeSampleResource` uses `example = "quarkus-core"` for the `extension` parameter
3. Documentation guidelines example uses `example = "main"` for version
4. Swagger UI at `/q/swagger-ui` shows `main` as the version example for all endpoints
5. Clicking "Try it out" → "Execute" on `/api/catalog` with default examples returns non-empty results
6. `./gradlew test` passes with zero failures
7. No runtime behavior changes -- only annotation metadata is modified

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Changing examples breaks OpenAPI schema validation tests | Very Low | Low | No tests assert on example values; examples are metadata-only |
| `main` version may not be intuitive for users expecting a numbered version | Low | Low | The `description` already says "Defaults to 'main' if omitted" |
| Other documentation or README references `3.27` as an example | Low | Low | Search for `3.27` across the codebase and update if found |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Update 5 example values across 4 resource files | 0.25 |
| Update documentation guidelines example | 0.1 |
| Verify Swagger UI rendering | 0.15 |
| **Total** | **~30 minutes** |

---

## Files Modified

### Production Code (4 files)
- `src/main/java/com/fvd/api/resources/CatalogResource.java` -- version example
- `src/main/java/com/fvd/api/resources/SearchResource.java` -- version example
- `src/main/java/com/fvd/api/resources/DocumentResource.java` -- version example
- `src/main/java/com/fvd/api/resources/CodeSampleResource.java` -- version example, extension example

### Documentation (1 file)
- `.project-guidelines-for-ai/documentation/guidelines.md` -- update annotation pattern example

### Test Code
- None -- no test changes needed

---

END OF FILE
