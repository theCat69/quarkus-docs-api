# Feature 28: Optional Version Parameter (Default to "main")

> **Dependencies**: None strictly required for the core behavior. The quarkiverse disclaimer in OpenAPI descriptions is conditional on Feature 27 (Quarkiverse Documentation Ingestion) being completed — see task split below.

Make the `version` query parameter optional on all API endpoints, defaulting to `"main"` when omitted. This reduces friction for consumers who primarily work with the latest docs.

> **Note**: `@DefaultValue("main")` on `@QueryParam` is intentionally NOT used because it bypasses `InputValidator.validateVersion()` — the validator would never see a null/blank value, so custom validation logic (like character allowlisting) would still run but the "required" semantics would be silently lost. Using `resolveVersion()` keeps the validation explicit and testable.

## Scope and behavior

- Define `DEFAULT_VERSION = "main"` constant in `InputValidator`.
- Add `InputValidator.resolveVersion(String version)` — returns the input if non-null and non-blank, otherwise returns `DEFAULT_VERSION`. Always calls `validateVersion()` on the resolved value before returning it.
- All 7 version-parameterized endpoints replace `validateVersion(version)` with `version = InputValidator.resolveVersion(version)`:
  - `SearchResource.searchFiles()`
  - `SearchResource.searchSections()`
  - `SearchResource.getSectionContent()`
  - `SearchResource.searchCodeSamples()`
  - `SearchResource.searchContent()`
  - `DocsResource.getDoc()`
  - `IndexResource.getIndex()`
- `SearchResource.listVersions()` is **unaffected** — it has no version parameter.
- OpenAPI `@Parameter` annotations change:
  - `required = false` (was `true`)
  - Add `@Schema(defaultValue = "main")` for OpenAPI spec generation
  - Update `description` to include `"Defaults to 'main' if omitted."`
  - Quarkiverse disclaimer (conditional on Feature 27): append `"When using 'main', results may include quarkiverse extension docs."` — this is a separate task that can be deferred if Feature 27 is not yet implemented.
- No change to service layer — services always receive a resolved (non-null, validated) version string.
- Response shape is unchanged. The only observable difference is that omitting `?version=` now returns 200 (using `"main"`) instead of 400.

## Internal interfaces

- **`InputValidator`** — add:
  - `public static final String DEFAULT_VERSION = "main"` constant.
  - `public static String resolveVersion(String version)` method — returns `version` if non-null and non-blank (after trimming), otherwise `DEFAULT_VERSION`. Calls `validateVersion()` on the resolved value. Returns the resolved version string.
- **`SearchResource`** — 5 methods updated (`searchFiles`, `searchSections`, `getSectionContent`, `searchCodeSamples`, `searchContent`): replace `InputValidator.validateVersion(version)` with `version = InputValidator.resolveVersion(version)`. Update `@Parameter` annotations.
- **`DocsResource.getDoc()`** — same changes: replace `validateVersion` with `resolveVersion`, update `@Parameter`.
- **`IndexResource.getIndex()`** — same changes: replace `validateVersion` with `resolveVersion`, update `@Parameter`.

## Response shape

No change to response shapes. The only behavioral change:

**Before** (version omitted):
```
GET /api/search/files?keywords=security
→ 400 Bad Request: "version must not be empty"
```

**After** (version omitted):
```
GET /api/search/files?keywords=security
→ 200 OK (uses version "main")
```

**Explicit version still works identically:**
```
GET /api/search/files?version=3.27&keywords=security
→ 200 OK (uses version "3.27")
```

## Tasks

- [ ] Add unit tests for `InputValidator.resolveVersion(null)` — returns `"main"`.
- [ ] Add unit tests for `InputValidator.resolveVersion("")` — returns `"main"`.
- [ ] Add unit tests for `InputValidator.resolveVersion("  ")` — returns `"main"` (blank/whitespace).
- [ ] Add unit tests for `InputValidator.resolveVersion("3.27")` — returns `"3.27"`.
- [ ] Add unit tests for `InputValidator.resolveVersion("invalid!version")` — throws `InvalidInputException`.
- [ ] Add `DEFAULT_VERSION` constant and `resolveVersion()` method to `InputValidator`.
- [ ] Update `SearchResource` (5 methods): replace `validateVersion(version)` with `version = InputValidator.resolveVersion(version)`. Update `@Parameter` annotations to `required = false`, add `@Schema(defaultValue = "main")`, update description with default note.
- [ ] Update `DocsResource.getDoc()`: same changes as SearchResource.
- [ ] Update `IndexResource.getIndex()`: same changes as SearchResource.
- [ ] Update breaking tests — the following tests assert `statusCode(400)` when version is missing and must be changed to assert `statusCode(200)` with `"main"` results:
  - `SearchResourceTest.testSearchFilesEndpointMissingVersion`
  - `SearchResourceTest.testSearchSectionsEndpointMissingVersion`
  - `SearchResourceTest.testSectionContentEndpointMissingVersion`
  - `SearchResourceTest.testSearchContentEndpointMissingVersion`
  - `SearchResourceTest.testSearchCodeSamplesEndpointMissingVersion`
  - `DocsResourceTest.testDocEndpointMissingVersion`
  - `IndexResourceTest.testIndexEndpointMissingVersion`
- [ ] Add integration tests: search without version returns 200 with `"main"` results.
- [ ] Add integration tests: doc retrieval without version returns 200 with `"main"` content.
- [ ] Add integration tests: index without version returns 200 with `"main"` index.
- [ ] Add integration tests: explicit version `"3.27"` still works as before.
- [ ] Verify `listVersions()` endpoint is unaffected (no version parameter).
- [ ] (Conditional on Feature 27) Update OpenAPI `@Parameter` descriptions to include quarkiverse disclaimer: `"When using 'main', results may include quarkiverse extension docs."`
- [ ] Verify all existing tests still pass after changes.

## Operational notes

- This is a backward-compatible change for all existing API consumers. Requests that previously included `?version=...` continue to work identically.
- The only observable change is that requests omitting `?version=` now succeed (200) instead of failing (400).
- Consumers relying on the 400 error as a validation signal should update their integration tests.
- The `"main"` default aligns with the website repo structure (Feature 26) where `"main"` is the primary version containing the latest docs.

## Implementation notes

_(To be filled during implementation)_
