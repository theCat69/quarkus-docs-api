# Feature 16: Full-text search

Add content-level search across documentation files so users can find matches within document bodies, not just file names or section titles.

## Scope and behavior

- New search capability over doc content with snippets and match offsets.
- Reuse existing cache/doc storage; avoid external dependencies in first iteration.
- Provide pagination for large result sets.

## Tasks

- Review indexing pipeline and doc storage to locate content available at search time.
- Define a lightweight full-text search approach (initial scan + caching or reuse keyword index where possible).
- Add search service method and endpoint parameters for content search.
- Include result snippets and match offsets in responses.
- Add pagination parameters (limit/offset) to full-text search responses.
- Add tests for relevance ordering and snippet generation.

## Tasks checklist

- [x] Review indexing pipeline and doc storage to locate content available at search time.
- [x] Define a lightweight full-text search approach (initial scan + caching or reuse keyword index where possible).
- [x] Add search service method and endpoint parameters for content search.
- [x] Include result snippets and match offsets in responses.
- [x] Add pagination parameters (limit/offset) to full-text search responses.
- [x] Add tests for relevance ordering and snippet generation.
