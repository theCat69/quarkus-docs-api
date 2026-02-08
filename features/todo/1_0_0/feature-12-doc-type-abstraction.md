# Feature 12: Doc type abstraction for multiple formats

Introduce an abstraction around doc parsing and storage so the system can support non-Asciidoc sources (e.g., Markdown) in the future.

## Scope and behavior

- Define a doc type interface for parsing sections, extracting keywords, and retrieving content.
- Provide an Asciidoc implementation that preserves current behavior.
- Prepare wiring to select implementation per repository/type.

## Tasks

- [ ] Identify current Asciidoc-specific entry points (parsing, keyword extraction, storage).
- [ ] Define interfaces for doc parsing, section extraction, and content access.
- [ ] Refactor existing Asciidoc implementation behind the interface.
- [ ] Update services to depend on the abstraction.
- [ ] Add tests for Asciidoc implementation via the new interfaces.
