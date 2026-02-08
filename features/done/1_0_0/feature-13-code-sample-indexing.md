# Feature 13: Code sample extraction and indexing

Extract all code samples when caching a new version and index them with section keywords plus boosted import keywords.

## Scope and behavior

- During cache build/refresh, parse Asciidoc to collect code blocks.
- Store code samples with linkage to source file and section.
- Index sample text plus section keywords; boost import statements (e.g., `jakarta.ws.rs.GET` with score +5).

## Tasks

- [x] Identify parsing points for code blocks in Asciidoc parser.
- [x] Define code sample model and storage location.
- [x] Add extraction during cache build/refresh.
- [x] Build code-sample index with keyword and import boosts.
- [x] Add tests for extraction and indexing behavior.
