# Task 07: Create Unified Search DTOs

> **Dependencies**: None — can start in parallel with any other task.

## Summary

Create the API-layer DTO `ChunkSearchResponse` in `com.fvd.api.dto` for the unified search endpoint response. This is the external-facing contract returned by `SearchResource`.

## Changes

### `src/main/java/com/fvd/api/dto/ChunkSearchResponse.java` *(created)*

Lombok `@Builder`, `@Getter`, `@NoArgsConstructor`, `@AllArgsConstructor`:

- `List<ChunkResult> results`
- `int total`
- `int limit`
- `int offset`

### `src/main/java/com/fvd/api/dto/ChunkResult.java` *(created)*

Lombok `@Builder`, `@Getter`, `@NoArgsConstructor`, `@AllArgsConstructor`:

- `String id`
- `String page`
- `String title`
- `String section`
- `String summary`
- `List<String> extensions`
- `List<String> topics`
- `double score`
- `String url`

Both classes should include OpenAPI `@Schema` annotations describing each field.

## Acceptance Criteria

- [ ] `ChunkSearchResponse` wraps a list of `ChunkResult` with pagination fields
- [ ] `ChunkResult` contains all 9 specified fields
- [ ] Lombok annotations generate builders, getters, and constructors
- [ ] OpenAPI `@Schema` annotations are present on each field
- [ ] `./gradlew compileJava` succeeds

## Files

- `src/main/java/com/fvd/api/dto/ChunkSearchResponse.java` *(created)*
- `src/main/java/com/fvd/api/dto/ChunkResult.java` *(created)*
