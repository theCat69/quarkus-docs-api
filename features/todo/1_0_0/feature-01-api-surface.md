# Feature 01: API surface and routing

This feature defines the HTTP API for the Quarkus docs MCP backend. All endpoints are GET-only and return JSON. The target documentation format is asciidoc. The MCP stdio server is the only caller, so the API is optimized for internal usage and stable semantics rather than public discovery.

## Scope and intent

- Provide a small, predictable surface for indexing, doc retrieval, and search.
- Ensure requests are version-aware and path-safe.
- Keep responses consistent so the MCP server can parse them quickly.

## Endpoints

### Index

- `GET /api/index?version={quarkus_version}`
- Purpose: return index for a specific Quarkus version.
- If cached, return from `.cache/<version>/file_index.json`.
- If not cached, fetch from GitHub, store, return.
- Upstream URL:
  - `https://api.github.com/repos/quarkusio/quarkus/contents/docs/src/main/asciidoc?ref=<quarkus_version>`

### Doc

- `GET /api/doc?version={quarkus_version}&path={file_path}`
- Purpose: return asciidoc content for a single file.
- If cached, read from `.cache/<version>/docs/<file_path>`.
- If not cached, fetch from GitHub contents API and store.
- Upstream URL:
  - `https://api.github.com/repos/quarkusio/quarkus/contents/<file_path>?ref=<quarkus_version>`

### File keyword search

- `GET /api/search/files?version={quarkus_version}&keywords=kw1,kw2`
- Purpose: return top 5-10 matching files based on keyword index.
- If full docs cache is missing, trigger streamed download and extraction.

### Section keyword search

- `GET /api/search/sections?version={quarkus_version}&keywords=kw1,kw2&filePaths=path1,path2`
- Purpose: return top 3-5 matching sections within the provided files.
- This relies on the keyword index with section metadata.

## Validation and errors

- Validate `version` is non-empty and safe for filesystem use.
- Validate `path` and `filePaths` are within `docs/` and do not traverse (`..`).
- Return:
  - 400 for invalid inputs.
  - 404 if document not found.
  - 502 for upstream GitHub errors.

## Response shape guidance

Responses should be stable and lightweight:

- Index: raw JSON array from GitHub contents API.
- Doc: `{ "path": "...", "content": "...", "format": "asciidoc" }`.
- Search files: `{ "results": [ { "path": "...", "score": 12.4 } ] }`.
- Search sections: `{ "results": [ { "path": "...", "section": "...", "start": 12, "end": 42, "score": 9.2 } ] }`.

## Tasks

- [ ] Define GET routes and DTOs in Quarkus.
- [ ] Implement input validation and error mapping.
- [ ] Add required `/api/health` endpoint.
- [ ] Document response JSON for index/doc/search endpoints.
