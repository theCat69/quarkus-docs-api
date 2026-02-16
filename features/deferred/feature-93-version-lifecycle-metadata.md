# Feature 93: Version Lifecycle Metadata

> **Dependencies**: None. Modifies the `CatalogResponse` and `CatalogService`. Backward-incompatible change to the `versions` field type (from `List<String>` to `List<VersionInfo>`).

## Summary

The catalog response returns versions as a flat list of strings (`["3.20", "3.27", "main"]`), providing no lifecycle information. AI agents must guess which version is "latest", which is "development", and whether a version is LTS. This feature enhances the versions response with structured metadata — status (`latest`, `stable`, `development`), LTS flag, and optional EOL date — driven by configuration in `application.properties`. This helps AI agents automatically select the most appropriate version.

## User Story

As an **AI agent selecting which Quarkus documentation version to query**, I want the catalog to include version lifecycle metadata (status, LTS, EOL) so that I can automatically pick the latest stable version without hardcoding version knowledge or guessing from version numbers.

## Motivation

### Current Behavior

```json
{
    "versions": ["3.20", "3.27", "main"]
}
```

An agent cannot tell which version is latest, which is development, or which to prefer. It must either ask the user or guess.

### Desired Behavior

```json
{
    "versions": [
        {"version": "3.27", "status": "latest", "lts": false},
        {"version": "3.20", "status": "stable", "lts": false},
        {"version": "main", "status": "development", "lts": false}
    ]
}
```

An agent can now filter for `status=latest` to get the most current stable version.

---

## Scope / Requirements

### R1: Create `VersionInfo` DTO

**New file:** `src/main/java/com/fvd/api/dto/VersionInfo.java`

```java
package com.fvd.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Version metadata with lifecycle information")
public class VersionInfo {

    @Schema(description = "Version identifier", example = "3.27")
    public String version;

    @Schema(description = "Lifecycle status: 'latest', 'stable', 'development', or 'eol'",
            example = "latest")
    public String status;

    @Schema(description = "Whether this is a Long-Term Support version", example = "false")
    public boolean lts;
}
```

### R2: Update `CatalogResponse`

**File:** `src/main/java/com/fvd/api/dto/CatalogResponse.java`

Change the `versions` field type:

```java
// Before:
public List<String> versions;

// After:
public List<VersionInfo> versions;
```

This is a **breaking change** for clients parsing `versions` as `List<String>`. Since the API is consumed primarily by the MCP server (controlled deployment), this is acceptable.

### R3: Configuration-Driven Version Metadata

**File:** `src/main/resources/application.properties`

```properties
# Version lifecycle metadata (status: latest, stable, development, eol)
app.version-meta.main.status=development
app.version-meta.main.lts=false
app.version-meta.3\\.27.status=latest
app.version-meta.3\\.27.lts=false
app.version-meta.3\\.20.status=stable
app.version-meta.3\\.20.lts=false
```

### R4: Create `VersionMetadataConfig`

**New file:** `src/main/java/com/fvd/api/config/VersionMetadataConfig.java`

```java
package com.fvd.api.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "app.version-meta")
public interface VersionMetadataConfig {

    Map<String, VersionEntry> versions();

    interface VersionEntry {
        @WithDefault("stable")
        String status();

        @WithDefault("false")
        boolean lts();
    }
}
```

Note: SmallRye Config maps `app.version-meta.<key>.status` to the `versions()` map automatically. The key is the version string (with dots escaped in `.properties` files).

### R5: Update `CatalogService`

**File:** `src/main/java/com/fvd/api/services/CatalogService.java`

Inject `VersionMetadataConfig` and build `VersionInfo` objects for each cached version:

```java
private List<VersionInfo> buildVersionInfos(List<String> cachedVersions) {
    return cachedVersions.stream()
            .map(v -> {
                var entry = versionMetadataConfig.versions().get(v);
                if (entry != null) {
                    return new VersionInfo(v, entry.status(), entry.lts());
                }
                // Default: unconfigured versions are "stable", not LTS
                return new VersionInfo(v, "stable", false);
            })
            .toList();
}
```

### R6: Update MetaService Filters

**File:** `src/main/java/com/fvd/api/services/MetaService.java`

The `buildFilters()` method currently returns `filters.versions` as `List<String>`. Update it to return `List<VersionInfo>` or keep it as `List<String>` for the meta endpoint (which is a discovery/summary endpoint). Decision: keep `filters.versions` as `List<String>` in meta — the meta endpoint is for discovery (version names), not lifecycle details. Agents use `/api/catalog` for full version metadata.

---

## Request/Response Examples

### Example 1: Catalog with version metadata

**Request:**
```
GET /api/catalog?version=3.27
```

**Response (200):**
```json
{
    "subjects": [ ... ],
    "extensions": [ ... ],
    "versions": [
        {"version": "3.27", "status": "latest", "lts": false},
        {"version": "3.20", "status": "stable", "lts": false},
        {"version": "main", "status": "development", "lts": false}
    ]
}
```

### Example 2: Version not configured (defaults)

If a version `3.15` exists in cache but has no configuration:

```json
{"version": "3.15", "status": "stable", "lts": false}
```

---

## Tasks

- [ ] Create `VersionInfo` DTO in `com.fvd.api.dto` with `version`, `status`, `lts` fields
- [ ] Create `VersionMetadataConfig` config mapping interface in `com.fvd.api.config`
- [ ] Update `CatalogResponse.versions` from `List<String>` to `List<VersionInfo>`
- [ ] Update `CatalogService` to inject `VersionMetadataConfig` and build `VersionInfo` objects
- [ ] Add version metadata configuration to `application.properties`
- [ ] Add version metadata for dev profile (`%dev.app.version-meta.*`)
- [ ] Add version metadata for test profile or use defaults
- [ ] Add unit test: configured version returns correct status and lts
- [ ] Add unit test: unconfigured version defaults to status="stable", lts=false
- [ ] Add unit test: "main" version returns status="development"
- [ ] Add integration test: `GET /api/catalog` returns `versions` as array of objects with `version`, `status`, `lts`
- [ ] Update existing catalog integration tests that assert on `versions` as `List<String>`
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /api/catalog` returns `versions` as an array of `VersionInfo` objects (not strings)
2. Each `VersionInfo` contains `version`, `status`, and `lts` fields
3. Version metadata is configuration-driven via `application.properties`
4. Unconfigured versions default to `status="stable"`, `lts=false`
5. `version=main` returns `status="development"` when configured
6. At least one version has `status="latest"`
7. Existing catalog subjects and extensions are unchanged
8. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Breaking change: `versions` type changes from `List<String>` to `List<VersionInfo>` | Expected | Medium | API is consumed by the MCP server (controlled deployment); coordinate update. Document in changelog. |
| SmallRye Config mapping with dotted version keys (e.g., `3.27`) requires escaping | Medium | Medium | Use `3\\.27` or alternative config format (YAML). Test config parsing explicitly. |
| Operators forget to update config when adding new versions | Medium | Low | Unconfigured versions get sensible defaults (`stable`, not LTS); no crash |
| "latest" status must be set on exactly one version | Medium | Low | Validation could be added but is not critical for v1; documentation suffices |
| Test fixtures break on new `versions` format | High | Low | Update all test assertions that check `versions` field; grep for `versions` in tests |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create `VersionInfo` DTO | 0.25 |
| Create `VersionMetadataConfig` | 0.5 |
| Update `CatalogResponse` and `CatalogService` | 1.0 |
| Add configuration properties | 0.5 |
| Unit tests for version metadata resolution | 1.0 |
| Integration tests | 1.0 |
| Update existing tests for new format | 1.0 |
| Run full test suite and fix regressions | 0.5 |
| **Total** | **~5.75 hours** |

---

## Files Modified

### New Production Files (2 files)
- `src/main/java/com/fvd/api/dto/VersionInfo.java` — version metadata DTO
- `src/main/java/com/fvd/api/config/VersionMetadataConfig.java` — SmallRye Config mapping interface

### Modified Production Files (3 files)
- `src/main/java/com/fvd/api/dto/CatalogResponse.java` — change `versions` type from `List<String>` to `List<VersionInfo>`
- `src/main/java/com/fvd/api/services/CatalogService.java` — inject `VersionMetadataConfig`, build `VersionInfo` objects
- `src/main/resources/application.properties` — add version metadata configuration

### Modified Test Files (estimated 2-3 files)
- Existing catalog tests that assert `versions` as strings need updating for object format
- `src/test/java/com/fvd/api/services/CatalogServiceTest.java` — update version assertions

### New Test Files (1 file)
- `src/test/java/com/fvd/api/config/VersionMetadataConfigTest.java` — unit tests for config parsing

---

END OF FILE
