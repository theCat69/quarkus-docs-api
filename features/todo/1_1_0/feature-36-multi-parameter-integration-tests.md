# Feature 36: Multi-Parameter Integration Tests

> **Dependencies**: Feature 31 (Space-Separated Keywords), Feature 35 (Wire Extension Parameter). Optionally Feature 33 (Section Search Overhaul) for sectionTitle tests.

Create a comprehensive integration test class that exercises combinations of search parameters to prove they work together correctly. This is a test-only feature — no production code changes.

## Scope and behavior

- Create `MultiParameterSearchTest.java` (`@QuarkusTest`) with 8+ tests covering parameter combinations.
- Seed realistic test data: 4+ doc files across 2 extensions (`quarkus-core` and `quarkus-oidc`), with sections, code samples, and varied keyword distributions.
- Tests must prove:
  1. Extension filtering reduces results vs. unfiltered.
  2. Keywords + extension + pagination work together.
  3. Multiple parameters compose correctly (no parameter is silently ignored).

## Test cases

1. **keywords + extension**: `keywords=security oidc&extension=quarkus-core` returns fewer results than `keywords=security oidc` alone.
2. **keywords + filePaths + extension**: `/sections?keywords=security&filePaths=security-overview.adoc&extension=quarkus-core` filters on all three.
3. **keywords + limit + offset + extension**: Verify pagination math is correct with extension filter (total reflects filtered count).
4. **keywords + sectionTitle + filePath + extension** (code-samples): All 4 filters applied simultaneously.
5. **stop words + extension**: `keywords=how does security work&extension=quarkus-core` strips stop words AND filters by extension.
6. **standalone section search + extension**: `/sections?keywords=auth&extension=quarkus-oidc` returns only quarkiverse sections.
7. **extension filter returns empty**: `keywords=security&extension=nonexistent-ext` returns 0 results.
8. **no extension returns all**: Same keywords without extension returns results from both extensions.

## Internal interfaces

No production code changes.

## Tasks

- [ ] Create test data setup: `@BeforeEach` test fixture seeding with 4+ adoc files:
  - `security-overview.adoc` (extension: `quarkus-core`, keywords: security, authentication, authorization)
  - `config.adoc` (extension: `quarkus-core`, keywords: configuration, properties)
  - `quarkiverse/quarkus-oidc/index.adoc` (extension: `quarkus-oidc`, keywords: security, oidc, authentication)
  - `quarkiverse/quarkus-oidc/configuration.adoc` (extension: `quarkus-oidc`, keywords: oidc, configuration)
- [ ] Test 1: keywords + extension filtering — `keywords=security&extension=quarkus-core` vs `keywords=security` (unfiltered has more results).
- [ ] Test 2: keywords + filePaths + extension — triple filter on sections endpoint.
- [ ] Test 3: keywords + limit + offset + extension — pagination with filter.
- [ ] Test 4: keywords + sectionTitle + filePath + extension on code-samples.
- [ ] Test 5: stop words + extension — `keywords=how does security work&extension=quarkus-core`.
- [ ] Test 6: section search + extension = `quarkus-oidc` only.
- [ ] Test 7: extension = `nonexistent-ext` returns empty.
- [ ] Test 8: no extension returns all extensions' results.
- [ ] Run all tests (`./gradlew test`) — all must pass.

## Acceptance Criteria

1. All 8+ multi-parameter tests pass.
2. Tests prove extension filtering reduces result count.
3. Tests prove stop word removal works in combination with extension filter.
4. Tests prove pagination is correct with filters applied.
5. No production code changes.

## Operational notes

- This test class serves as a regression safety net for the v1.1.0 parameter changes.
- Test data should be realistic enough to produce multi-result sets so filtering can be meaningfully verified.
- If Feature 33 (sectionTitle on sections) is not yet done when this is implemented, defer sectionTitle-related assertions or skip with `@Disabled`.
- Follow the test seeding pattern used in `SearchResourceTest` — clean `build/test-cache`, reset schema, invalidate caches in `@BeforeEach`.

---
