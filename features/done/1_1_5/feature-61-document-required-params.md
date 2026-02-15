# Feature 61: Document Required Parameters for /api/documents

> **Dependencies**: None. This is a documentation-only change to OpenAPI annotations — no behavioral changes.

## Summary

The `/api/documents` endpoint requires either `path` or `keywords` to be provided, but the OpenAPI spec marks both as `required = false`. The endpoint returns 400 if neither is provided, but this constraint is buried in the operation description. An AI agent consuming the OpenAPI spec has no clear signal that at least one of these parameters is required, leading to trial-and-error discovery. This feature makes the constraint explicit and prominent in the OpenAPI annotations.

## User Story

As an **AI agent consuming the OpenAPI spec**, I want the `/api/documents` endpoint to clearly communicate that either `path` or `keywords` must be provided so that I can construct valid requests without trial-and-error.

## Motivation

The current `@Operation` description on `DocumentResource.getDocuments()` (line 36–42) states:

```java
@Operation(
    summary = "Get document by path or search by keywords",
    description = "If 'path' is provided, returns a single document with full structured content " +
            "including sections and code blocks. If 'keywords' is provided, searches documents " +
            "and returns matching results with scores. Path takes precedence if both are provided. " +
            "Returns 400 if neither path nor keywords is provided."
)
```

The description mentions the 400 behavior, but it's at the end of a long sentence. The individual `@Parameter` annotations on `path` (line 68–73) and `keywords` (line 75–80) both say `required = false`, which is technically correct (neither is individually required) but misleading for automated consumers.

OpenAPI 3.0 has no native `oneOf` or `anyOf` for query parameters. The best approach for AI consumers is to make the constraint highly visible in the operation description, add clarifying text to each parameter's description, and provide explicit usage examples via `@APIResponse` annotations.

### Current state (`DocumentResource.java`)

- **Line 37**: `summary = "Get document by path or search by keywords"` — doesn't mention the constraint
- **Line 69**: `description = "Document path relative to docs directory. If provided, returns single document."` — no mention that one of path/keywords is required
- **Line 76**: `description = "Space-separated search keywords. Required if path not provided."` — hints at the relationship but isn't prominent
- **Line 130**: `throw new InvalidInputException("Either 'path' or 'keywords' must be provided")` — the runtime enforcement

---

## Requirements

### R1: Update `@Operation` description to front-load the constraint

**Current** (line 38–41):
```java
description = "If 'path' is provided, returns a single document with full structured content " +
        "including sections and code blocks. If 'keywords' is provided, searches documents " +
        "and returns matching results with scores. Path takes precedence if both are provided. " +
        "Returns 400 if neither path nor keywords is provided."
```

**New**:
```java
description = "REQUIRED: At least one of 'path' or 'keywords' must be provided. " +
        "Returns 400 if neither is specified.\n\n" +
        "Mode 1 — Path lookup: If 'path' is provided, returns a single document with full " +
        "structured content including sections and code blocks.\n" +
        "Mode 2 — Keyword search: If 'keywords' is provided, searches documents and returns " +
        "matching results with scores. Supports optional 'subject' and 'extension' filters.\n\n" +
        "If both 'path' and 'keywords' are provided, path takes precedence (keyword search is ignored)."
```

The constraint is now the first sentence. The two usage modes are clearly separated. Newlines improve readability in Swagger UI.

### R2: Update `@Parameter` descriptions for `path` and `keywords`

**`path` parameter** (line 69) — current:
```java
description = "Document path relative to docs directory. If provided, returns single document."
```
**New**:
```java
description = "Document path relative to docs directory. If provided, returns a single document " +
        "with full content. Either 'path' or 'keywords' must be provided."
```

**`keywords` parameter** (line 76) — current:
```java
description = "Space-separated search keywords. Required if path not provided."
```
**New**:
```java
description = "Space-separated search keywords for document search. " +
        "Either 'keywords' or 'path' must be provided."
```

### R3: Update `@Operation` summary for clarity

**Current** (line 37):
```java
summary = "Get document by path or search by keywords"
```
**New**:
```java
summary = "Get document by path or search by keywords (at least one required)"
```

### R4: No behavioral changes

- The endpoint logic in `DocumentResource.getDocuments()` (lines 110–131) is unchanged.
- The `InvalidInputException` message (line 130) remains unchanged.
- No new validation logic is added.

---

## Tasks

- [ ] Update `@Operation` summary on `DocumentResource.getDocuments()` to include "(at least one required)"
- [ ] Update `@Operation` description to front-load the "REQUIRED: at least one of" constraint
- [ ] Update `@Parameter` description for `path` to mention the mutual requirement
- [ ] Update `@Parameter` description for `keywords` to mention the mutual requirement
- [ ] Verify Swagger UI renders the updated descriptions correctly (manual check in dev mode)
- [ ] Run `./gradlew test` — all tests pass (no behavioral changes)

---

## Acceptance Criteria

1. The `@Operation` description starts with "REQUIRED: At least one of 'path' or 'keywords' must be provided"
2. The `@Operation` summary includes "(at least one required)"
3. Both `path` and `keywords` `@Parameter` descriptions mention the mutual requirement
4. The two usage modes (path lookup, keyword search) are clearly documented with "Mode 1" / "Mode 2" labels
5. No behavioral changes — endpoint returns the same responses as before
6. `./gradlew test` passes with zero failures
7. No new files created

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Longer descriptions clutter Swagger UI | Low | Low | Newlines in description improve readability; summary stays concise |
| AI agents ignore `@Operation` description and only read `@Parameter` | Medium | Low | The constraint is repeated in both `path` and `keywords` `@Parameter` descriptions |
| Future parameter additions require updating the constraint text | Low | Low | The constraint text is in one place (`@Operation` description); parameter descriptions are supplementary |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Update 4 annotation strings in `DocumentResource.java` | 0.25 |
| Verify Swagger UI rendering | 0.25 |
| Run tests | 0.10 |
| **Total** | **~35 minutes** |

---

## Files Affected

| File | Change Type |
|------|-------------|
| `src/main/java/com/fvd/api/resources/DocumentResource.java` | Modify — update 4 OpenAPI annotation strings |

---

END OF FILE
