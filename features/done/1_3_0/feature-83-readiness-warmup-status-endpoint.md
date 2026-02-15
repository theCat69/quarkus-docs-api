# Feature 83: Readiness and Warmup Status Endpoint

> **Dependencies**: None. This is a self-contained observability enhancement. Uses the existing `quarkus-smallrye-health` dependency already present in `build.gradle`.

## Summary

The API has no way for consumers to determine whether the cache warmup has completed. The `CacheWarmupJob` runs at startup (priority 200) and downloads, extracts, and indexes documentation for configured versions — a process that can take 30-60 seconds. During this time, API calls return empty results or errors, with no indication that the system is still initializing. This feature adds a `GET /api/status` endpoint that exposes warmup progress, integrates with SmallRye Health readiness checks so orchestrators (Kubernetes, Docker Compose) can delay routing traffic until warmup completes, and allows MCP servers to poll until ready before serving agent requests.

## User Story

As an **AI agent consuming the API through an MCP server**, I want to check whether the API is ready to serve requests (`GET /api/status`) so that I can wait for warmup to complete rather than receiving empty results, and so the MCP server can report accurate readiness to agents instead of silently returning no data.

## Motivation

### Current Behavior (No Status Visibility)

1. Application starts, `CacheWarmupJob.onStartup()` begins downloading docs
2. MCP server starts and immediately begins routing agent requests
3. `GET /api/catalog` returns empty subjects/extensions (index not built yet)
4. `GET /api/search?keywords=quarkus` returns `{"results":[], "totalCount":0}`
5. Agent assumes no docs exist and gives up or hallucinates
6. ~45 seconds later, warmup completes silently — but agent has already moved on

**First catalog call took ~6 seconds** during warmup — the server was busy downloading and the response was delayed but incomplete.

### Desired Behavior (Status Awareness)

1. Application starts, `CacheWarmupJob.onStartup()` begins downloading docs
2. MCP server calls `GET /api/status` → `{"ready": false, "warmupProgress": {"completed": 0, "total": 3, ...}}`
3. MCP server waits, polling every 5 seconds
4. `GET /api/status` → `{"ready": false, "warmupProgress": {"completed": 2, "total": 3, "currentVersion": "3.20"}}`
5. `GET /api/status` → `{"ready": true, "warmupProgress": {"completed": 3, "total": 3}}`
6. MCP server begins routing agent requests — all data available
7. Kubernetes readiness probe (`/q/health/ready`) also returns UP only after warmup completes

---

## Scope / Requirements

### R1: Create `WarmupStatusTracker` — Shared State for Warmup Progress

**New file:** `src/main/java/com/fvd/cache/services/WarmupStatusTracker.java`

**Package:** `com.fvd.cache.services`

An `@ApplicationScoped` bean that tracks warmup progress. The `CacheWarmupJob` updates it as each version completes. Resource endpoints and health checks read from it.

```java
package com.fvd.cache.services;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class WarmupStatusTracker {

    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean warmupStarted = new AtomicBoolean(false);
    private final AtomicReference<String> currentVersion = new AtomicReference<>(null);
    private final List<String> completedVersions = Collections.synchronizedList(new ArrayList<>());
    private volatile int totalVersions = 0;

    public void warmupStarted(List<String> versions) {
        warmupStarted.set(true);
        totalVersions = versions.size();
    }

    public void versionCompleted(String version) {
        completedVersions.add(version);
        currentVersion.set(null);
    }

    public void versionStarted(String version) {
        currentVersion.set(version);
    }

    public void warmupCompleted() {
        ready.set(true);
        currentVersion.set(null);
    }

    public boolean isReady() { return ready.get(); }
    public boolean isWarmupStarted() { return warmupStarted.get(); }
    public String getCurrentVersion() { return currentVersion.get(); }
    public List<String> getCompletedVersions() { return List.copyOf(completedVersions); }
    public int getTotalVersions() { return totalVersions; }
    public int getCompletedCount() { return completedVersions.size(); }
}
```

### R2: Update `CacheWarmupJob` to Report Progress to `WarmupStatusTracker`

**File:** `src/main/java/com/fvd/cache/jobs/CacheWarmupJob.java`

Inject `WarmupStatusTracker` and call its methods at each stage:

```java
// At start of onStartup():
warmupStatusTracker.warmupStarted(versions);

// Before processing each version:
warmupStatusTracker.versionStarted(version);

// After building indexes for each version:
warmupStatusTracker.versionCompleted(version);

// At end of onStartup() (after "Cache warmup completed" log):
warmupStatusTracker.warmupCompleted();
```

**Edge cases:**
- When all versions are already cached (`versionsToWarm.isEmpty()`), call `warmupStarted(versions)` then immediately `warmupCompleted()` — the system is ready.
- When `configuredVersions` is empty, call `warmupCompleted()` — no warmup needed, system is ready.
- When a version fails during warmup (caught exception), still call `versionCompleted(version)` to avoid stalling the progress tracker — log the failure but continue.

### R3: Create `StatusResource` — `GET /api/status` Endpoint

**New file:** `src/main/java/com/fvd/api/resources/StatusResource.java`

**Package:** `com.fvd.api.resources`

```java
package com.fvd.api.resources;

@Path("/api/status")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Tag(name = "Status", description = "API readiness and warmup status")
public class StatusResource {

    private final WarmupStatusTracker warmupStatusTracker;
    private final CacheService cacheService;

    @GET
    @Operation(
            summary = "Get API readiness and warmup status",
            description = "Returns the current warmup progress and readiness state. " +
                    "MCP servers should poll this endpoint until 'ready' is true before " +
                    "routing agent requests. Returns 503 when not ready."
    )
    @APIResponse(responseCode = "200", description = "API is ready",
            content = @Content(schema = @Schema(implementation = StatusResponse.class)))
    @APIResponse(responseCode = "503", description = "API is not ready — warmup in progress",
            content = @Content(schema = @Schema(implementation = StatusResponse.class)))
    public Response getStatus() {
        StatusResponse status = buildStatus();
        int httpStatus = status.ready ? 200 : 503;
        return Response.status(httpStatus).entity(status).build();
    }

    private StatusResponse buildStatus() { ... }
}
```

**HTTP status code semantics:**
- `200` when `ready=true` — safe to route requests
- `503 Service Unavailable` when `ready=false` — warmup in progress, retry later

This allows MCP servers to use a simple status-code check instead of parsing JSON.

### R4: Create `StatusResponse` DTO

**New file:** `src/main/java/com/fvd/api/dto/StatusResponse.java`

```java
package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.List;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusResponse {

    public boolean ready;
    public List<String> cachedVersions;
    public WarmupProgress warmupProgress;

    @RegisterForReflection
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WarmupProgress {
        public int completed;
        public int total;
        public List<String> versionsCompleted;
        public String currentVersion;
    }
}
```

### R5: Integrate with SmallRye Health Readiness Check

**New file:** `src/main/java/com/fvd/cache/health/WarmupReadinessCheck.java`

```java
package com.fvd.cache.health;

import com.fvd.cache.services.WarmupStatusTracker;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
@RequiredArgsConstructor
public class WarmupReadinessCheck implements HealthCheck {

    private final WarmupStatusTracker warmupStatusTracker;

    @Override
    public HealthCheckResponse call() {
        boolean ready = warmupStatusTracker.isReady();
        return HealthCheckResponse.named("Cache warmup")
                .status(ready)
                .withData("completedVersions", warmupStatusTracker.getCompletedCount())
                .withData("totalVersions", warmupStatusTracker.getTotalVersions())
                .build();
    }
}
```

This makes `/q/health/ready` return `DOWN` until warmup completes. Kubernetes readiness probes pointing to this endpoint will delay traffic routing.

### R6: Add OpenAPI Annotations to `StatusResource`

The `StatusResource` must have full OpenAPI annotations following project conventions. The `@Tag(name = "Status")` groups it in the API spec. The `StatusResponse` schema is documented via `@Schema(implementation = ...)`.

---

## Technical Design

### State Machine

```
Application starts
  │
  ├─ configuredVersions empty → warmupCompleted() → READY
  │
  ├─ all versions cached → warmupStarted() → warmupCompleted() → READY
  │
  └─ versions to warm
       │
       warmupStarted([v1, v2, v3])
       │
       ├─ versionStarted("3.20") → downloading, indexing...
       │   └─ versionCompleted("3.20")
       │
       ├─ versionStarted("3.27") → downloading, indexing...
       │   └─ versionCompleted("3.27")
       │
       ├─ versionStarted("main") → downloading, indexing, quarkiverse...
       │   └─ versionCompleted("main")
       │
       warmupCompleted() → READY
```

### Thread Safety

The `WarmupStatusTracker` uses:
- `AtomicBoolean` for `ready` and `warmupStarted` — lock-free boolean state
- `AtomicReference<String>` for `currentVersion` — lock-free reference swap
- `Collections.synchronizedList` for `completedVersions` — thread-safe adds
- `volatile int` for `totalVersions` — set once, read many

The `CacheWarmupJob` runs on the main thread (startup event). The `StatusResource` and health check read from the tracker on request threads. The atomic/volatile fields ensure visibility across threads.

### No Authentication on `/api/status`

The status endpoint does not require authentication — it exposes no sensitive data (only version names and counts). This matches the project pattern where all API endpoints are unauthenticated.

---

## Request/Response Examples

### Example 1: Warmup in progress

**Request:**
```
GET /api/status
```

**Response (503):**
```json
{
    "ready": false,
    "cachedVersions": ["3.27"],
    "warmupProgress": {
        "completed": 1,
        "total": 3,
        "versionsCompleted": ["3.27"],
        "currentVersion": "3.20"
    }
}
```

### Example 2: Warmup complete

**Request:**
```
GET /api/status
```

**Response (200):**
```json
{
    "ready": true,
    "cachedVersions": ["3.20", "3.27", "main"],
    "warmupProgress": {
        "completed": 3,
        "total": 3,
        "versionsCompleted": ["3.20", "3.27", "main"],
        "currentVersion": null
    }
}
```

### Example 3: No versions configured

**Request:**
```
GET /api/status
```

**Response (200):**
```json
{
    "ready": true,
    "cachedVersions": [],
    "warmupProgress": {
        "completed": 0,
        "total": 0,
        "versionsCompleted": [],
        "currentVersion": null
    }
}
```

### Example 4: Kubernetes readiness probe

**Request:**
```
GET /q/health/ready
```

**Response during warmup (503):**
```json
{
    "status": "DOWN",
    "checks": [
        {
            "name": "Cache warmup",
            "status": "DOWN",
            "data": {
                "completedVersions": 1,
                "totalVersions": 3
            }
        }
    ]
}
```

**Response after warmup (200):**
```json
{
    "status": "UP",
    "checks": [
        {
            "name": "Cache warmup",
            "status": "UP",
            "data": {
                "completedVersions": 3,
                "totalVersions": 3
            }
        }
    ]
}
```

---

## Implementation Notes

### Startup Event Priority

The `CacheWarmupJob` uses `@Priority(200)` for its startup event observer. The `SqliteSchemaInitializer` uses `@Priority(100)` — it runs first. The `WarmupStatusTracker` is a plain `@ApplicationScoped` bean with no startup observer — it is lazily initialized on first access. This means:

1. `SqliteSchemaInitializer` creates tables (priority 100)
2. `CacheWarmupJob` starts warmup and calls `warmupStatusTracker.warmupStarted()` (priority 200)
3. If a request arrives before step 2, `warmupStatusTracker.isReady()` returns `false` (default `AtomicBoolean(false)`)

This is correct — the system is not ready until warmup completes.

### MCP Server Polling Pattern

The MCP server should poll `GET /api/status` with:
- **Interval:** 5 seconds
- **Timeout:** 120 seconds (abort if warmup exceeds this)
- **Check:** HTTP status 200 means ready; 503 means retry

```python
# MCP server startup (pseudo-code)
while not ready and elapsed < 120:
    response = GET /api/status
    if response.status == 200:
        ready = True
    else:
        sleep(5)
```

### Test Profile Behavior

In the test profile (`%test`), `quarkus.scheduler.enabled=false` and no warmup is configured. The `WarmupStatusTracker` will never have `warmupCompleted()` called unless explicitly triggered in tests. Tests that need the system to be ready should either:
1. Call `warmupStatusTracker.warmupCompleted()` in test setup
2. Or not depend on the readiness state (most existing tests don't)

### Graceful Error Handling in Warmup

When a version fails during warmup (e.g., GitHub download failure), the current `CacheWarmupJob` catches the exception and continues to the next version. The `WarmupStatusTracker` should still mark the failed version as "completed" (with a failure note) to avoid stalling progress. The `ready` flag is set to `true` at the end of `onStartup()` regardless of individual version failures — the system is as ready as it can be.

---

## Tasks

- [ ] Create `WarmupStatusTracker` in `com.fvd.cache.services` — atomic state tracking for warmup progress
- [ ] Create `StatusResponse` DTO in `com.fvd.api.dto` — response model with `WarmupProgress` inner class
- [ ] Create `StatusResource` in `com.fvd.api.resources` — `GET /api/status` with OpenAPI annotations
- [ ] Create `WarmupReadinessCheck` in `com.fvd.cache.health` — SmallRye Health readiness check
- [ ] Update `CacheWarmupJob.onStartup()` — inject `WarmupStatusTracker` and call progress methods
- [ ] Handle edge case: all versions already cached — call `warmupStarted()` then `warmupCompleted()`
- [ ] Handle edge case: no configured versions — call `warmupCompleted()` immediately
- [ ] Handle edge case: version warmup failure — still call `versionCompleted()` to avoid stalling
- [ ] Return HTTP 503 when `ready=false`, 200 when `ready=true`
- [ ] Add unit tests for `WarmupStatusTracker`:
    - Initial state: `ready=false`
    - After `warmupCompleted()`: `ready=true`
    - Progress tracking: `versionStarted()`, `versionCompleted()` update correctly
    - Thread safety: concurrent reads don't throw
- [ ] Add unit tests for `StatusResource`:
    - Returns 503 with progress when not ready
    - Returns 200 with full status when ready
- [ ] Add integration test: `GET /api/status` returns valid JSON with expected structure
- [ ] Add integration test: `/q/health/ready` includes "Cache warmup" check
- [ ] Verify existing tests are not affected by the new readiness check
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /api/status` returns `200` with `"ready": true` after warmup completes
2. `GET /api/status` returns `503` with `"ready": false` and warmup progress during warmup
3. Response includes `cachedVersions` listing all versions with cached documentation
4. Response includes `warmupProgress.completed`, `warmupProgress.total`, `warmupProgress.versionsCompleted`, and `warmupProgress.currentVersion`
5. `/q/health/ready` returns `DOWN` during warmup and `UP` after warmup completes
6. Health check includes `"name": "Cache warmup"` with `completedVersions` and `totalVersions` data
7. When no versions are configured for warmup, the system reports `ready=true` immediately
8. When all versions are already cached, the system reports `ready=true` after the check completes
9. The endpoint has full OpenAPI annotations and appears in the API spec under "Status" tag
10. All existing tests pass unchanged
11. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| SmallRye Health readiness check blocks all endpoints during warmup (not just search) | Medium | High | This is intentional for Kubernetes; the `/api/status` endpoint itself is not behind the readiness gate — it's always accessible |
| `WarmupStatusTracker` not initialized before first request arrives | Low | Medium | Default `AtomicBoolean(false)` means `ready=false` before any warmup method is called — correct behavior |
| Test profile: health check returns DOWN because `warmupCompleted()` never called | Medium | Medium | In test setup, manually call `warmupStatusTracker.warmupCompleted()` or ensure `CacheWarmupJob` runs in test |
| Race condition between version completion and status read | Very Low | Low | Atomic/volatile fields ensure visibility; slight inconsistency (e.g., `completed=1` but `versionsCompleted` has 2 entries) is acceptable |
| MCP server doesn't implement polling — ignores 503 responses | Medium | Low | Document the polling pattern; 503 is a standard "retry later" signal |
| Warmup failure leaves `ready=false` permanently | Low | High | Call `warmupCompleted()` in a `finally` block in `onStartup()` so it runs even if warmup partially fails |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create `WarmupStatusTracker` | 0.5 |
| Create `StatusResponse` DTO | 0.25 |
| Create `StatusResource` with OpenAPI annotations | 1.0 |
| Create `WarmupReadinessCheck` health check | 0.5 |
| Update `CacheWarmupJob` to report progress | 1.0 |
| Handle edge cases (no versions, all cached, failures) | 0.5 |
| Unit tests for `WarmupStatusTracker` | 0.5 |
| Unit tests for `StatusResource` | 0.5 |
| Integration tests (`/api/status` + `/q/health/ready`) | 1.0 |
| Verify existing tests and fix test-profile issues | 0.5 |
| **Total** | **~6.25 hours** |

---

## Files Modified

### New Production Files (4 files)
- `src/main/java/com/fvd/cache/services/WarmupStatusTracker.java` — shared warmup progress state
- `src/main/java/com/fvd/api/dto/StatusResponse.java` — response DTO with `WarmupProgress`
- `src/main/java/com/fvd/api/resources/StatusResource.java` — `GET /api/status` endpoint
- `src/main/java/com/fvd/cache/health/WarmupReadinessCheck.java` — SmallRye Health readiness check

### Modified Production Files (1 file)
- `src/main/java/com/fvd/cache/jobs/CacheWarmupJob.java` — inject `WarmupStatusTracker`, call progress methods

### New Test Files (estimated 2-3 files)
- `src/test/java/com/fvd/cache/services/WarmupStatusTrackerTest.java` — unit tests for progress tracking
- `src/test/java/com/fvd/api/resources/StatusResourceTest.java` — unit/integration tests for status endpoint
- `src/test/java/com/fvd/cache/health/WarmupReadinessCheckTest.java` — unit test for health check (optional)

### Unchanged Files
- `src/main/java/com/fvd/cache/services/CacheService.java` — `listCachedVersions()` reused as-is
- `src/main/resources/application.properties` — no new config needed; `quarkus-smallrye-health` already in `build.gradle`
- All existing resource classes — no changes needed
- All existing test classes — no changes needed

---

## Dependencies

- **`quarkus-smallrye-health`** — already in `build.gradle`, provides `/q/health/ready` endpoint and `@Readiness` annotation
- **No other feature dependencies** — this is a self-contained observability enhancement

---

END OF FILE
