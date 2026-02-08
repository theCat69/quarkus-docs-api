# Feature 08: Section content retrieval API

Add a new endpoint that returns section content (raw Asciidoc) for a specific section in a doc/version. This reuses existing cache/parsing and does not change storage or indexing.

## Scope and behavior

- Endpoint under search API to fetch a section’s content and metadata.
- Inputs: version, doc path, and section identifier (anchor/id/title based on existing models).
- Output: section metadata plus raw Asciidoc content.
- Errors: 400 for invalid params, 404 for missing version/path/section, reuse existing error response style.

## Tasks

- [ ] Identify stable section identifier(s) in current models/parsing.
- [ ] Define request/response DTOs for section content retrieval.
- [ ] Add service method to load section content from cached doc/parsed model.
- [ ] Add JAX-RS endpoint and validation.
- [ ] Add tests for success and error cases.
- [ ] Add OpenAPI annotations and example response.
