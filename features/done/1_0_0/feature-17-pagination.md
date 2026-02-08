# Feature 17: Pagination for search results

Introduce `limit` and `offset` parameters across search endpoints to keep large result sets manageable and consistent.

## Scope and behavior

- Add optional pagination params to file, section, and code sample searches.
- Apply defaults and validation to prevent excessive response sizes.
- Ensure pagination is consistent across services and responses.

## Tasks

- Identify all search endpoints returning lists (files/sections/code samples).
- Add optional `limit`/`offset` parameters with validation and defaults.
- Apply pagination in search services and response DTOs.
- Add tests for pagination behavior, boundaries, and empty pages.

## Tasks checklist

- [x] Identify all search endpoints returning lists (files/sections/code samples).
- [x] Add optional `limit`/`offset` parameters with validation and defaults.
- [x] Apply pagination in search services and response DTOs.
- [x] Add tests for pagination behavior, boundaries, and empty pages.
