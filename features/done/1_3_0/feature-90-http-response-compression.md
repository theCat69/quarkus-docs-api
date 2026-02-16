# Feature 90: HTTP Response Compression (gzip)

> **Dependencies**: None. This is a configuration-only change with no code modifications.

## Summary

HTTP response compression is not verified or explicitly enabled. Large API responses — such as `GET /api/documents?brief=false` returning 2 full documents (186KB+) or `GET /api/catalog` returning the full extensions list — would benefit significantly from gzip compression. For MCP servers relaying API data to AI agents, reduced transfer size directly reduces latency and bandwidth. This feature enables Quarkus HTTP compression for JSON responses via `application.properties` configuration.

## User Story

As an **MCP server relaying API responses to AI agents**, I want the API to compress large JSON responses with gzip so that transfer time is reduced and my downstream latency is minimized, especially for full document retrievals and batch operations.

## Motivation

### Current Behavior

```
GET /api/documents?path=security-overview.adoc&version=3.27
→ 200 OK
Content-Type: application/json
Content-Length: 93000
(no Content-Encoding header — uncompressed)
```

A full document response of ~93KB is sent uncompressed. Batch document retrieval with `brief=false` can return 186KB+.

### Desired Behavior

```
GET /api/documents?path=security-overview.adoc&version=3.27
Accept-Encoding: gzip
→ 200 OK
Content-Type: application/json
Content-Encoding: gzip
Content-Length: ~12000
(gzip-compressed body — ~87% smaller)
```

### Compression Savings Estimate

| Response | Uncompressed | Gzip compressed | Savings |
|----------|-------------|-----------------|---------|
| Single full document | ~93KB | ~12KB | ~87% |
| Batch 2 documents (brief=false) | ~186KB | ~22KB | ~88% |
| Search results (20 items) | ~15KB | ~3KB | ~80% |
| Catalog response | ~8KB | ~2KB | ~75% |
| Small responses (<1KB) | ~0.5KB | ~0.5KB | ~0% (overhead) |

JSON compresses exceptionally well due to repetitive structure.

---

## Scope / Requirements

### R1: Enable HTTP Compression

**File:** `src/main/resources/application.properties`

```properties
# HTTP response compression
quarkus.http.enable-compression=true
```

Quarkus enables gzip compression for responses when the client sends `Accept-Encoding: gzip`. This is handled by Vert.x (Quarkus's underlying HTTP server) automatically.

### R2: Configure Compressible Media Types

**File:** `src/main/resources/application.properties`

Quarkus compresses `text/html`, `text/plain`, `text/xml`, and `text/css` by default. JSON is **not** compressed by default. Add:

```properties
quarkus.http.compress-media-types=application/json,text/plain
```

This ensures `application/json` responses are compressed when the client supports it.

### R3: No Code Changes Required

Quarkus handles compression at the Vert.x layer, below JAX-RS. No resource or filter changes are needed. The `CacheHeaderFilter` computes ETags **before** compression (at the JAX-RS entity level), so ETags remain correct — they are based on the uncompressed content, which is the standard HTTP behavior.

### R4: Client Compatibility

Compression is only applied when the client sends `Accept-Encoding: gzip`. Clients that do not send this header receive uncompressed responses. This is fully backward compatible.

---

## Request/Response Examples

### Example 1: Client supports gzip

**Request:**
```
GET /api/documents?path=security-overview.adoc&version=3.27
Accept-Encoding: gzip
```

**Response (200):**
```
HTTP/1.1 200 OK
Content-Type: application/json
Content-Encoding: gzip
Transfer-Encoding: chunked

(gzip-compressed body)
```

### Example 2: Client does not support gzip

**Request:**
```
GET /api/documents?path=security-overview.adoc&version=3.27
```

**Response (200):**
```
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 93000

(uncompressed body)
```

---

## Tasks

- [ ] Add `quarkus.http.enable-compression=true` to `application.properties`
- [ ] Add `quarkus.http.compress-media-types=application/json,text/plain` to `application.properties`
- [ ] Add integration test: `GET /api/search?keywords=security` with `Accept-Encoding: gzip` returns `Content-Encoding: gzip` header
- [ ] Add integration test: response body is valid JSON when decompressed
- [ ] Add integration test: request without `Accept-Encoding` still returns valid uncompressed JSON
- [ ] Verify ETag values are consistent between compressed and uncompressed responses (same ETag for same content)
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `quarkus.http.enable-compression=true` is set in `application.properties`
2. `quarkus.http.compress-media-types` includes `application/json`
3. Requests with `Accept-Encoding: gzip` receive `Content-Encoding: gzip` in the response
4. Requests without `Accept-Encoding: gzip` receive uncompressed responses (backward compatible)
5. ETag values are identical for the same content regardless of compression
6. All existing tests pass unchanged
7. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| CPU overhead from compressing every response | Low | Low | Gzip at default compression level adds <1ms per response; JSON compresses very fast |
| Small responses (~100 bytes) grow slightly after gzip headers | Low | Low | Vert.x has a minimum size threshold; responses under ~1KB may not be compressed |
| `Content-Length` header disappears (replaced by `Transfer-Encoding: chunked`) | Medium | Low | Standard HTTP behavior when compression is applied; well-behaved clients handle this |
| ETag mismatch between compressed and uncompressed responses | Low | High | ETags are computed at JAX-RS layer (pre-compression); Vert.x compresses after — ETags remain stable |
| RestAssured tests fail when decompressing gzip responses | Low | Medium | RestAssured handles `Accept-Encoding: gzip` automatically; verify in integration tests |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Add configuration properties | 0.25 |
| Integration tests for compression behavior | 1.0 |
| Verify ETag consistency | 0.5 |
| Run full test suite | 0.25 |
| **Total** | **~2.0 hours** |

---

## Files Modified

### Modified Production Files (1 file)
- `src/main/resources/application.properties` — add compression configuration (2 lines)

### New Test Files (1 file)
- `src/test/java/com/fvd/common/filters/CompressionIntegrationTest.java` — integration tests verifying gzip behavior

---

END OF FILE
