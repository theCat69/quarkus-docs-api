# Feature 11: SQLite index storage

Replace file-based indexes with SQLite-backed indexes to improve retrieval and search performance.

## Scope and behavior

- Store keyword and section indexes in SQLite instead of flat files.
- Keep query semantics the same for search endpoints.
- Provide a migration or rebuild path during cache refresh.

## Tasks

- [x] Inventory current index file formats and access patterns.
- [x] Design SQLite schema for keyword and section indexes.
- [x] Implement index writer/reader with SQLite.
- [x] Update services to use SQLite store.
- [x] Add migration/rebuild logic during cache refresh.
- [x] Add tests for index read/write and search correctness.
