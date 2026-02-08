# Feature 15: Fuzzy section retrieval

Improve section content retrieval to support keyword-aware fuzzy matching and best-match selection instead of strict exact title matching. This changes default behavior to return the closest relevant section when an exact match is not provided.

## Scope and behavior

- Inputs remain version, doc path, and section identifier, but identifier can be a keyword or partial title.
- Use keyword index and section title similarity to select the best candidate.
- Return match metadata to help clients understand why a section was selected.
- Maintain 400/404 behavior for invalid params and missing docs, but prefer best-match over 404 when candidates exist.

## Tasks

- Identify current section retrieval flow and exact-match logic in resource/service/store.
- Define matching strategy using keyword index scoring plus title similarity; set thresholds and tie-breakers.
- Implement best-match selection in section retrieval service and update error handling.
- Extend response DTO with match metadata (matchedTitle, matchScore, matchType).
- Add tests for partial titles, keyword matches, misspellings, and ambiguous sections.
- Update API docs and OpenAPI examples to describe fuzzy behavior.

## Tasks checklist

- [x] Identify current section retrieval flow and exact-match logic in resource/service/store.
- [x] Define matching strategy using keyword index scoring plus title similarity; set thresholds and tie-breakers.
- [x] Implement best-match selection in section retrieval service and update error handling.
- [x] Extend response DTO with match metadata (matchedTitle, matchScore, matchType).
- [x] Add tests for partial titles, keyword matches, misspellings, and ambiguous sections.
- [x] Update API docs and OpenAPI examples to describe fuzzy behavior.
