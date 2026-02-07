# Feature 02: Cache and storage layout

This feature defines the filesystem cache layout for Quarkus documentation and search metadata. The cache is optimized for low latency reads and deterministic paths. All docs are stored as asciidoc.

## Cache layout

```
.cache/
  <quarkus_version>/
    docs/                      # extracted asciidoc docs
      ...                      # mirrors docs/src/main/asciidoc
    file_index.json             # raw GitHub contents API response
    keyword_index.json          # computed keywords and section index
```

## Storage rules

- `file_index.json` is stored exactly as returned by the GitHub contents API.
- Docs are stored under `.cache/<version>/docs` with the same relative path
  they have under `docs/src/main/asciidoc` in the Quarkus repo.
- `keyword_index.json` is generated from cached docs; it is not fetched from GitHub.

## Persistence requirements

- Cache must be durable across restarts.
- Reads should not trigger downloads unless a file is missing.
- Writes should be atomic where possible (write temp + rename).

## Internal interfaces

- `CacheService.ensureVersionDir(version)`
- `IndexStore.readRaw(version)` / `IndexStore.writeRaw(version, json)`
- `DocStore.read(path)` / `DocStore.write(path, content)`
- `KeywordIndexStore.read(version)` / `KeywordIndexStore.write(version, json)`

## Tasks

- [ ] Create cache directory layout on demand.
- [ ] Implement read/write helpers for index, docs, keyword index.
- [ ] Ensure safe atomic writes for JSON files.
- [ ] Guard all reads/writes with version/path validation.
