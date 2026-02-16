# Task 05: Create DocChunkBuilder

> **Dependencies**: Task 04 (DocChunkStore, DocChunk model).

## Summary

Create `DocChunkBuilder` in `com.fvd.indexs.services` — the service that replaces `KeywordIndexer` and `CodeSampleIndexer`. It uses `AsciidocParser.parseSections()` to split `.adoc` files into section-level chunks, extracts metadata, and batch-inserts `DocChunk` records via `DocChunkStore`. Needs `DocStore` to read file content.

## Changes

### `src/main/java/com/fvd/indexs/services/DocChunkBuilder.java` *(created)*

- `@ApplicationScoped`, `@RequiredArgsConstructor`
- Injects: `DocParser`, `DocChunkStore`, `UrlBuilder`, **`DocStore`**
- **build(String version, List\<String\> filePaths)** — processes core docs (extension = `quarkus-core`)
- **build(String version, List\<String\> filePaths, String extension)** — processes extension docs
- **build(String version, Map\<String, List\<String\>\> extensionFiles)** — iterates map, delegates to single-extension overload

**Signatures use `List<String>` and `Map<String, List<String>>` to match the existing indexer calling convention** (e.g., `CacheWarmupJob` passes `List<String>` file paths, not `List<Path>`).

#### Per-file processing logic

1. Read file content via `docStore.read(version, filePath)`
2. Call `docParser.parseSections(content)` → `List<Section>`
3. Call `docParser.extractMetadata(content)` → topics, extensions, summary
4. For each section, build a `DocChunk`:
   - `id` = `slugify(page) + "#" + slugify(section.title())`
   - `version` = the version parameter
   - `page` = filename without `.adoc`
   - `title` = document title (first `=` heading)
   - `section` = section heading
   - `url` = `urlBuilder.buildUrl(page, section.title())`
   - `topics` / `extensions` from metadata (plus extension param)
   - `summary` = first sentence of section content
   - `content` = raw section text
5. Call `docChunkStore.deleteByVersion(version)` before insert (idempotent rebuild)
6. Call `docChunkStore.insertBatch(version, chunks)`

## Acceptance Criteria

- [ ] All three `build()` overloads accept `List<String>` / `Map<String, List<String>>` (not `Path`)
- [ ] `DocStore` is injected and used to read file content
- [ ] `version` is set on every `DocChunk`
- [ ] Each `==` section produces exactly one chunk
- [ ] Chunk IDs are deterministic and unique per page+section
- [ ] `deleteByVersion` is called before insert for idempotent rebuilds
- [ ] `./gradlew compileJava` succeeds

## Files

- `src/main/java/com/fvd/indexs/services/DocChunkBuilder.java` *(created)*
