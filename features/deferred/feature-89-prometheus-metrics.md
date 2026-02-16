# Feature 89: Enable Prometheus Metrics

> **Dependencies**: None. This is a self-contained infrastructure feature. Requires adding a new Gradle dependency.

## Summary

The API has no observability metrics — no request counts, no latency histograms, no cache hit/miss ratios. When serving AI agents at scale through MCP servers, operators need visibility into API performance, traffic patterns, and bottleneck identification. This feature adds `quarkus-micrometer-registry-prometheus` to expose a `/q/metrics` endpoint with both built-in HTTP server metrics and custom application metrics for search latency, cache hit/miss ratio, and request counts per endpoint.

## User Story

As an **API operator monitoring the Quarkus Docs API in production**, I want Prometheus-compatible metrics exposed at `/q/metrics` so that I can monitor request rates, latency percentiles, error rates, and cache efficiency using Grafana or similar dashboards.

## Motivation

### Current Behavior

No metrics endpoint exists. The only observability is log output. There is no way to know:
- How many requests each endpoint receives
- What the P50/P95/P99 latency is for search queries
- Whether the document parse cache is effective
- If any endpoint is becoming a bottleneck

### Desired Behavior

`GET /q/metrics` returns Prometheus-format metrics including:
- HTTP server request duration histograms (built-in via Micrometer)
- Custom `api_search_duration_seconds` histogram for search operations
- Custom `api_cache_hits_total` / `api_cache_misses_total` counters
- Custom `api_requests_total` counter with `endpoint` label

---

## Scope / Requirements

### R1: Add Gradle Dependency

**File:** `build.gradle`

```groovy
implementation 'io.quarkus:quarkus-micrometer-registry-prometheus'
```

This enables `/q/metrics` automatically via Quarkus auto-configuration. No application.properties changes needed for the basic setup — Quarkus enables the endpoint by default.

### R2: Enable HTTP Server Metrics

Quarkus + Micrometer automatically instruments HTTP server requests when `quarkus-micrometer-registry-prometheus` is on the classpath. This provides:
- `http_server_requests_seconds_count` — request count per path, method, status
- `http_server_requests_seconds_sum` — total duration
- `http_server_requests_seconds_bucket` — latency histogram buckets

No code changes needed — just the dependency.

### R3: Custom Search Latency Metric

**File:** `src/main/java/com/fvd/api/services/QuickSearchService.java` (and `DocumentService.java`, `CodeSampleService.java`)

Add a `Timer` to measure search execution time:

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class QuickSearchService {

    private final MeterRegistry meterRegistry;

    public QuickSearchResponse search(...) {
        return Timer.builder("api.search.duration")
                .tag("endpoint", "quick-search")
                .description("Search execution time")
                .register(meterRegistry)
                .record(() -> doSearch(...));
    }
}
```

Apply the same pattern to `DocumentService.searchDocuments()` (tag `endpoint=document-search`) and `CodeSampleService.searchCodeSamples()` (tag `endpoint=code-sample-search`).

### R4: Custom Cache Hit/Miss Counters

**File:** `src/main/java/com/fvd/docs/parser/DocParser.java` (or wherever the document parse cache is checked)

Add counters for cache hits and misses:

```java
meterRegistry.counter("api.cache.document", "result", "hit").increment();
meterRegistry.counter("api.cache.document", "result", "miss").increment();
```

### R5: Configuration

**File:** `src/main/resources/application.properties`

```properties
# Metrics configuration
quarkus.micrometer.export.prometheus.enabled=true

# Disable metrics in test profile to avoid test interference
%test.quarkus.micrometer.enabled=false
```

### R6: Exclude Metrics from CacheHeaderFilter

The existing `CacheHeaderFilter` already skips non-`api/` paths (line 54: `if (!path.startsWith("api/")) return;`). Since `/q/metrics` does not start with `api/`, no change is needed.

---

## Request/Response Examples

### Example 1: Prometheus metrics

**Request:**
```
GET /q/metrics
```

**Response (200, text/plain):**
```
# HELP http_server_requests_seconds
# TYPE http_server_requests_seconds histogram
http_server_requests_seconds_bucket{method="GET",uri="/api/search",status="200",le="0.05"} 15.0
http_server_requests_seconds_bucket{method="GET",uri="/api/search",status="200",le="0.1"} 42.0
...
# HELP api_search_duration_seconds Search execution time
# TYPE api_search_duration_seconds histogram
api_search_duration_seconds_bucket{endpoint="quick-search",le="0.1"} 35.0
...
# HELP api_cache_document_total Document parse cache results
# TYPE api_cache_document_total counter
api_cache_document_total{result="hit"} 150.0
api_cache_document_total{result="miss"} 12.0
```

---

## Tasks

- [ ] Add `io.quarkus:quarkus-micrometer-registry-prometheus` dependency to `build.gradle`
- [ ] Add `quarkus.micrometer.export.prometheus.enabled=true` to `application.properties`
- [ ] Add `%test.quarkus.micrometer.enabled=false` to test profile
- [ ] Inject `MeterRegistry` into `QuickSearchService` and wrap search in `Timer`
- [ ] Inject `MeterRegistry` into `DocumentService` and wrap `searchDocuments()` in `Timer`
- [ ] Inject `MeterRegistry` into `CodeSampleService` and wrap `searchCodeSamples()` in `Timer`
- [ ] Add cache hit/miss counters to the document parse cache
- [ ] Add integration test: `GET /q/metrics` returns 200
- [ ] Add integration test: response contains `http_server_requests` metric
- [ ] Verify `CacheHeaderFilter` does not add cache headers to `/q/metrics`
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /q/metrics` returns 200 with Prometheus-format text
2. HTTP server request metrics (`http_server_requests_seconds`) are present
3. Custom `api.search.duration` timer records search latency with `endpoint` tag
4. Custom `api.cache.document` counter tracks cache hits and misses
5. Metrics endpoint is excluded from `CacheHeaderFilter` (no `Cache-Control`/`ETag` headers)
6. Metrics are disabled in the test profile (`%test.quarkus.micrometer.enabled=false`)
7. No existing tests break
8. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Micrometer dependency conflicts with existing Quarkus BOM | Low | High | `quarkus-micrometer-registry-prometheus` is a first-party Quarkus extension; version managed by BOM |
| Timer overhead on hot search path | Low | Low | Micrometer Timers add ~100ns per recording; negligible vs. search times (10-500ms) |
| High-cardinality labels on `http_server_requests` (query param variations) | Medium | Medium | Quarkus Micrometer normalizes URI templates (e.g., `/api/search` not `/api/search?keywords=...`); no custom action needed |
| Test failures if metrics beans are injected but Micrometer is disabled | Medium | Medium | Use `%test.quarkus.micrometer.enabled=false` and ensure `MeterRegistry` injection has a no-op fallback in test profile |
| Exposing `/q/metrics` publicly leaks operational data | Low | Low | API is internal/behind MCP server; add documentation note about restricting access in production |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Add dependency and configuration | 0.5 |
| Add search timers to 3 service classes | 1.5 |
| Add cache hit/miss counters | 0.5 |
| Integration tests | 1.0 |
| Run full test suite and fix regressions | 0.5 |
| **Total** | **~4.0 hours** |

---

## Files Modified

### Modified Production Files (5 files)
- `build.gradle` — add `quarkus-micrometer-registry-prometheus` dependency
- `src/main/resources/application.properties` — add metrics configuration
- `src/main/java/com/fvd/api/services/QuickSearchService.java` — inject `MeterRegistry`, add search timer
- `src/main/java/com/fvd/api/services/DocumentService.java` — inject `MeterRegistry`, add search timer
- `src/main/java/com/fvd/api/services/CodeSampleService.java` — inject `MeterRegistry`, add search timer

### Modified Production Files (1 file, cache counters)
- `src/main/java/com/fvd/docs/parser/DocParser.java` — add cache hit/miss counters (exact file depends on where document parse cache is implemented)

### New Test Files (1 file)
- `src/test/java/com/fvd/api/metrics/MetricsIntegrationTest.java` — integration test for `/q/metrics` endpoint

---

END OF FILE
