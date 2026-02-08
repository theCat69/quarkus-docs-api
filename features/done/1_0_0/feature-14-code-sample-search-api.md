# Feature 14: Code sample search API

Add an endpoint to search code samples directly with keywords and return code sample content.

## Scope and behavior

- Add new search endpoint that queries the code-sample index.
- Inputs: version, keywords, optional filters (path/section).
- Output: code sample content plus metadata (file, section, score).
- Reuse existing search scoring semantics where possible.

## Tasks

- [x] Define request/response DTOs for code sample search.
- [x] Implement service method to query code-sample index.
- [x] Add JAX-RS endpoint and validation.
- [x] Add tests for success and error cases.
- [x] Add OpenAPI descriptions for the new endpoint.
