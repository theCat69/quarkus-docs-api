# Feature 62: Populate Extension Descriptions from Quarkiverse

> **Dependencies**: None. Can be implemented independently, but should be completed before Feature 63 (Extension Keywords in Catalog) to avoid rework on `ExtensionInfo`.

## Summary

In `CatalogService.buildExtensionList()` (line 115), extension descriptions are hardcoded to `""`:

```java
extensions.add(new ExtensionInfo(name, displayName, "", entry.getValue()));
```

Quarkiverse extensions have metadata in their Antora component descriptors (`antora.yml`) that could provide descriptions. This feature extracts the `title` from `antora.yml` during quarkiverse ingestion and uses it to populate extension descriptions in the catalog. For `quarkus-core`, a hardcoded description is used since it doesn't come from an Antora source.

## User Story

As an **AI agent browsing the catalog**, I want each extension to have a meaningful description so that I can understand what an extension does without having to search its documents.

## Motivation

The `/api/catalog` response currently returns extensions like:

```json
{
  "name": "quarkus-openapi-generator",
  "displayName": "Openapi Generator",
  "description": "",
  "docCount": 3
}
```

The empty `description` field provides no value. The Antora `antora.yml` component descriptor at each quarkiverse extension's `start_path` root typically contains:

```yaml
name: quarkus-openapi-generator
title: Quarkus OpenAPI Generator
version: ~
```

We can extract the `title` field during zip extraction and store it alongside the extension's docs. Additionally, the first paragraph of an extension's main page (usually `index.adoc`) often contains a one-sentence description that could serve as the extension description.

### Current data flow

1. `QuarkiverseService.fetchAndExtractAll()` → iterates `ResolvedContentSource` entries → calls `processExtension()` → calls `zipExtractor.extractDocs()`
2. `QuarkiverseZipExtractor.extractDocs()` → extracts `.adoc` files from `modules/ROOT/pages/` → returns list of extracted paths
3. `CatalogService.buildExtensionList()` → iterates `KeywordIndex.files` → groups by extension → creates `ExtensionInfo` with `description = ""`

The zip extraction step (2) already processes the zip stream and has access to `antora.yml`, but currently ignores it. The `antora.yml` file sits at the `start_path` root (e.g., `docs/antora.yml`), not inside `modules/ROOT/pages/`.

### Key files

- `QuarkiverseZipExtractor.java` — extracts docs from zip, needs to also extract `antora.yml`
- `CatalogService.java` — builds extension list, needs to read stored descriptions
- `ExtensionInfo.java` — already has `description` field (currently always `""`)
- `ContentSource.java` — has `url` and `start_path` fields

---

## Requirements

### R1: Extract `antora.yml` title during zip extraction

**Modify `QuarkiverseZipExtractor.extractDocs()`** to also look for the `antora.yml` file at the `start_path` root within the zip stream:

- The expected zip entry path pattern is `{repo-branch}/{start_path}/antora.yml` (or `{repo-branch}/antora.yml` if `start_path` is empty)
- When found, parse the YAML content to extract the `title` field
- Write the title to a metadata file at `{outputDir}/.extension-title` (a simple text file containing just the title string)
- If `antora.yml` is not found or has no `title`, skip gracefully — no metadata file is written

**Implementation approach**: Since `ZipInputStream` is sequential, check each entry against the `antora.yml` path pattern as the zip is being processed. This avoids a second pass.

The `antora.yml` path in the zip would be: `{repo-branch-prefix}/{start_path}/antora.yml`. The `start_path` is already available as a parameter to `extractDocs()`.

### R2: Read extension title in `CatalogService.buildExtensionList()`

**Modify `CatalogService.buildExtensionList()`** to read the stored title for each extension:

- For each extension name, check for the metadata file at `{cacheDir}/{version}/docs/quarkiverse/{extensionName}/.extension-title`
- If the file exists, use its content as the description
- If not found, use `""` (backward compatible)
- For `quarkus-core`, use a hardcoded description: `"Core Quarkus framework documentation"`

This requires injecting `CacheService` into `CatalogService` (which is already injected — see line 33).

**Version-specific behavior — important**: Quarkiverse extension docs are **only extracted for the `main` version**. `QuarkiverseService` hardcodes the output to `cacheService.versionDir("main")` (see `QuarkiverseService.java` line 167). This means:

- When `version = "main"` (or defaulting to `main`), quarkiverse extensions will have `.extension-title` files and descriptions will be populated.
- When `version != "main"` (e.g., `"3.27"`), the quarkiverse directories **do not exist** in that version's cache. `buildExtensionList()` may still list quarkiverse extensions if they appear in the `KeywordIndex` for that version, but their `.extension-title` files will not be found, so `description` will fall back to `""`.
- This is the correct and expected behavior — no cross-version file reads should occur. The lookup path must use the **requested `version`**, not hardcode `"main"`.
- If a future feature extracts quarkiverse docs for other versions, descriptions will automatically be populated without changes to `CatalogService`.

### R3: Parse `antora.yml` with Jackson YAML

The project already uses Jackson YAML for Antora playbook parsing (`AntoraPlaybookParser`). Create a minimal model class for the component descriptor:

```java
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AntoraComponentDescriptor {
    public String name;
    public String title;
    public Object version; // can be "~" (null), a string, or a number
}
```

Place in `com.fvd.quarkiverse.models` alongside the existing `AntoraPlaybook`, `ContentConfig`, and `ContentSource` models.

### R4: No changes to `ExtensionInfo`

The `ExtensionInfo` DTO already has a `description` field (line 17). No structural changes are needed — only the value being populated changes from `""` to the actual title.

---

## Tasks

- [ ] Create `AntoraComponentDescriptor` model in `com.fvd.quarkiverse.models` with `name` and `title` fields
- [ ] Modify `QuarkiverseZipExtractor.extractDocs()` to detect `antora.yml` in the zip stream and extract the `title` field
- [ ] Write extracted title to `{outputDir}/.extension-title` metadata file
- [ ] Modify `CatalogService.buildExtensionList()` to read `.extension-title` for each extension and populate `description`
- [ ] Add hardcoded description for `quarkus-core`: `"Core Quarkus framework documentation"`
- [ ] Add unit tests for `QuarkiverseZipExtractor`:
  - Zip contains `antora.yml` with `title: "My Extension"` → `.extension-title` file is written with `"My Extension"`
  - Zip does not contain `antora.yml` → no `.extension-title` file written, no error
  - `antora.yml` has no `title` field → no `.extension-title` file written
  - `antora.yml` contains malformed YAML (e.g., invalid syntax) → no `.extension-title` file written, warning logged, no exception thrown
- [ ] Add unit tests for `CatalogService.buildExtensionList()`:
  - Extension with `.extension-title` file → description is populated
  - Extension without `.extension-title` file → description is `""`
  - `quarkus-core` → description is `"Core Quarkus framework documentation"`
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. Quarkiverse extensions with an `antora.yml` containing a `title` field have their description populated in the catalog response
2. Extensions without `antora.yml` or without a `title` field have `description = ""` (backward compatible)
3. `quarkus-core` has description `"Core Quarkus framework documentation"`
4. The `antora.yml` title extraction does not slow down zip extraction significantly (it's parsed from the same zip stream)
5. `AntoraComponentDescriptor` model is placed in `com.fvd.quarkiverse.models`
6. `.extension-title` metadata file is written to the extension's cache directory
7. All existing tests pass
8. New unit tests cover the title extraction and description population paths
9. Malformed `antora.yml` does not cause extraction failure — a warning is logged and the extension is skipped gracefully

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Some `antora.yml` files don't have a `title` field | Medium | Low | Graceful fallback to `""` — no error thrown |
| `antora.yml` appears before `.adoc` files in zip stream, or vice versa | Low | Low | Both are processed in the same sequential pass; order doesn't matter since title is written to a separate file |
| Jackson YAML parsing fails on malformed `antora.yml` | Low | Low | Wrap parsing in try-catch; log warning and skip |
| `.extension-title` file persists across cache refreshes with stale data | Low | Medium | The file is written on every extraction; `CatalogService` cache is invalidated on refresh |
| `quarkus-core` hardcoded description becomes stale | Very Low | Low | It's a generic description; can be updated as needed |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create `AntoraComponentDescriptor` model | 0.25 |
| Modify `QuarkiverseZipExtractor` to extract title | 1.0 |
| Modify `CatalogService` to read and populate descriptions | 0.75 |
| Unit tests for zip extractor title extraction | 1.0 |
| Unit tests for catalog service description population | 0.75 |
| Run tests and verify | 0.25 |
| **Total** | **~4 hours** |

---

## Files Affected

| File | Change Type |
|------|-------------|
| NEW: `src/main/java/com/fvd/quarkiverse/models/AntoraComponentDescriptor.java` | Create — YAML model for `antora.yml` component descriptor |
| `src/main/java/com/fvd/quarkiverse/services/QuarkiverseZipExtractor.java` | Modify — detect and parse `antora.yml`, write `.extension-title` |
| `src/main/java/com/fvd/api/services/CatalogService.java` | Modify — read `.extension-title`, populate `description`, hardcode `quarkus-core` description |
| `src/test/java/com/fvd/quarkiverse/services/QuarkiverseZipExtractorTest.java` | Modify — add tests for title extraction |
| Existing or new test file for `CatalogService` | Modify — add tests for description population |

---

END OF FILE
