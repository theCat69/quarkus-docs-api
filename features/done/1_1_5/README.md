# Quarkus Docs API v1.1.5 Feature Specifications

## Overview

Version 1.1.5 focuses on **bug fixes, API quality improvements, and AI/MCP consumer ergonomics**. The 11 features span critical bug fixes (search accuracy, subject classification, language filtering), API documentation clarity, catalog enrichment, response quality, and API ergonomics. Together they ensure accurate pagination counts, correct classification of core docs, cleaner response content, richer catalog metadata, actionable validation errors, and a lightweight discovery mode for AI agents.

## Release Summary

| Aspect | Description |
|--------|-------------|
| Version | 1.1.5 |
| Type | Bug fixes, API quality improvements, AI/MCP consumer ergonomics |
| Goal | Fix incorrect totalCount, broken subject classification, language filter edge cases; improve OpenAPI docs, catalog metadata, response quality, and API ergonomics |
| Database | SQLite (unchanged) |
| Breaking Changes | None structural. Behavioral: language values normalized to lowercase in new indexes; `matchedKeywords` in responses change from stemmed to original forms |

## Features

### Critical Bug Fixes

| Feature | File | Priority | Description |
|---------|------|----------|-------------|
| [Feature 57: Fix totalCount Mismatch](feature-57-fix-totalcount-mismatch.md) | `feature-57-*.md` | HIGH | Push subject and language filters into `SearchService` so `totalCount` reflects filtered results; remove manual pagination from 3 API service classes |
| [Feature 58: Fix Subject Classification for Core Docs](feature-58-fix-subject-classification.md) | `feature-58-*.md` | HIGH | Update 11 default regex patterns in `SubjectDeriver.loadDefaultPatterns()` to match bare filenames (e.g., `security-overview.adoc` → `security`, not `misc`) |
| [Feature 60: Fix Language Filter on Code Samples](feature-60-fix-language-filter.md) | `feature-60-*.md` | MEDIUM | Normalize language to lowercase during indexing in `AsciidocParser`; normalize filter input in `SearchService` for consistent matching |

### API Documentation

| Feature | File | Priority | Description |
|---------|------|----------|-------------|
| [Feature 59: Fix OpenAPI Example Values](feature-59-fix-openapi-examples.md) | `feature-59-*.md` | LOW | Update `example = "3.27"` to `example = "main"` across 4 resources; update extension example to `quarkus-core` |
| [Feature 61: Document Required Parameters](feature-61-document-required-params.md) | `feature-61-*.md` | LOW | Front-load "REQUIRED: at least one of path/keywords" in `/api/documents` OpenAPI annotations; clarify two usage modes |

### Catalog Enrichment

| Feature | File | Priority | Description |
|---------|------|----------|-------------|
| [Feature 62: Populate Extension Descriptions](feature-62-populate-extension-descriptions.md) | `feature-62-*.md` | MEDIUM | Extract `title` from quarkiverse `antora.yml` during zip extraction; populate `ExtensionInfo.description` in catalog |
| [Feature 63: Add Extension Keywords to Catalog](feature-63-extension-keywords-in-catalog.md) | `feature-63-*.md` | MEDIUM | Add `List<String> keywords` field to `ExtensionInfo`; aggregate top-15 keywords per extension from keyword index |

### Response Quality

| Feature | File | Priority | Description |
|---------|------|----------|-------------|
| [Feature 64: Strip AsciiDoc Artifacts](feature-64-strip-asciidoc-artifacts.md) | `feature-64-*.md` | MEDIUM | Create `AsciiDocCleaner` utility to remove `include::`, `ifdef::`, `////` comments, `xref:` markup from descriptions and snippets |
| [Feature 66: Return Original Keywords in Results](feature-66-original-keywords-in-results.md) | `feature-66-*.md` | MEDIUM | Return original search terms (`"security"`) instead of stemmed forms (`"secur"`) in `matchedKeywords` response field |

### API Ergonomics

| Feature | File | Priority | Description |
|---------|------|----------|-------------|
| [Feature 65: Validate Version and Subject Parameters](feature-65-validate-version-subject-params.md) | `feature-65-*.md` | MEDIUM | Return 400 with list of valid values for invalid `version` or `subject` parameters instead of silent empty results |
| [Feature 67: Add Lightweight Document Search](feature-67-lightweight-document-search.md) | `feature-67-*.md` | MEDIUM | Add `brief=true` query parameter to `/api/documents` to return metadata-only results (~260x smaller response) for AI agent discovery |

## Implementation Order

Features 57 and 60 have a dependency relationship. Feature 65 benefits from Feature 57 being done first. Feature 62 should ideally precede Feature 63 to avoid rework on `ExtensionInfo`. All other features are independent.

```
Batch 1 — Critical Bug Fixes (implement first):
  Feature 58: Fix Subject Classification (independent)
  Feature 57: Fix totalCount Mismatch (independent, should precede 60 and 65)
     └── Feature 60: Fix Language Filter (depends on Feature 57)

Batch 2 — API Documentation (independent, low effort):
  Feature 59: Fix OpenAPI Examples (independent)
  Feature 61: Document Required Params (independent)

Batch 3 — Catalog Enrichment (coordinate ordering):
  Feature 62: Populate Extension Descriptions (should precede 63)
     └── Feature 63: Extension Keywords in Catalog (benefits from 62 being done first)

Batch 4 — Response Quality (independent):
  Feature 64: Strip AsciiDoc Artifacts (independent)
  Feature 66: Original Keywords in Results (independent)

Batch 5 — API Ergonomics (independent, benefits from batch 1):
  Feature 65: Validate Version and Subject Params (benefits from Feature 57)
  Feature 67: Lightweight Document Search (independent)
```

## Impact Summary

| Metric | Change |
|--------|--------|
| Production files modified | ~20 files |
| Production files created | 2 new files (`AsciiDocCleaner.java`, `AntoraComponentDescriptor.java`) |
| Test files modified/created | ~15–20 files |
| API surface changes | 1 new query parameter (`brief`), 1 new DTO field (`ExtensionInfo.keywords`), behavioral changes to `matchedKeywords` and `totalCount` |
| Behavioral changes | `totalCount` values corrected, subject classification fixed for core docs, language normalized to lowercase, `matchedKeywords` shows original terms, invalid params return 400 |
| Documentation-only changes | Features 59, 61 (OpenAPI annotations only) |

## Testing Strategy

- All existing tests must pass after each feature (`./gradlew test`)
- New unit tests for each feature verify the specific fix or enhancement
- Integration tests verify end-to-end behavior via HTTP endpoints (totalCount correctness, 400 responses for invalid params, brief mode response structure)
- Features 59 and 61 (annotation-only changes) require manual Swagger UI verification but no new test logic
- Parameterized tests preferred for pattern matching (Features 58, 60)

## Dependencies

- No new external dependencies
- Uses existing: Jackson YAML (for `antora.yml` parsing in Feature 62), JUnit 5 `junit-jupiter-params`, Lombok `@UtilityClass`
- Feature 60 depends on Feature 57 for optimal implementation (language filter moves to `SearchService`)
- Feature 63 should follow Feature 62 to avoid rework on `ExtensionInfo` constructor
- Feature 65 benefits from Feature 57 (subject filtering moved into `SearchService`)

## Estimated Effort

| Feature | Hours |
|---------|-------|
| Feature 57 — Fix totalCount Mismatch | ~5.25 |
| Feature 58 — Fix Subject Classification | ~1.75 |
| Feature 59 — Fix OpenAPI Examples | ~0.5 |
| Feature 60 — Fix Language Filter | ~1.85 |
| Feature 61 — Document Required Params | ~0.6 |
| Feature 62 — Populate Extension Descriptions | ~4.0 |
| Feature 63 — Extension Keywords in Catalog | ~3.25 |
| Feature 64 — Strip AsciiDoc Artifacts | ~4.5 |
| Feature 65 — Validate Version and Subject Params | ~4.25 |
| Feature 66 — Original Keywords in Results | ~4.5 |
| Feature 67 — Lightweight Document Search | ~4.0 |
| **Total** | **~34.45 hours** |

---

END OF FILE
