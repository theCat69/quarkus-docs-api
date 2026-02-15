# Feature 75: Parse & Index Document Metadata

> **Dependencies**: None. This is a foundation feature that other features (76, 77) depend on.

## Summary

Core Quarkus documentation files contain rich header attributes (`:categories:`, `:topics:`, `:extensions:`, `:summary:`, `:diataxis-type:`) that are completely ignored by the current indexing pipeline. Out of ~275 documents with `:categories:` and ~259 with `:topics:`, none of this metadata is extracted, stored, or made available to downstream services. This feature extends `AsciidocParser` to extract document metadata from AsciiDoc headers, introduces a `DocumentMetadata` model, adds a `document_metadata` SQLite table, and persists metadata during indexing — making it available to `SubjectDeriver`, `DocumentService`, and future features without changing any API response shapes.

## User Story

As an **AI agent consuming the API through an MCP server**, I want the documentation system to understand each document's categories, topics, and related extensions so that downstream features (subject classification, filtering, search ranking) can use this structured metadata instead of relying on fragile file-path heuristics.

## Motivation

### Current Behavior

The `AsciidocParser` extracts sections, code blocks, and keywords from document content, but completely ignores the attribute header block at the top of every AsciiDoc file. For example, `security-oidc-code-flow-authentication.adoc` contains:

```asciidoc
= OpenID Connect (OIDC) Authorization Code Flow mechanism for protecting web applications
include::_attributes.adoc[]
:categories: security,web
:topics: security,oidc,authentication,authorization
:extensions: io.quarkus:quarkus-oidc
:summary: OIDC Authorization Code Flow mechanism for protecting web applications
:diataxis-type: reference
```

None of these attributes are parsed or stored. The `KeywordIndexer` builds file-level and section-level keyword indexes but has no concept of document metadata. The `SubjectDeriver` relies on path-regex patterns and classifies 2,548 documents as "misc" because it cannot see `categories: security,web`.

### Desired Behavior

During indexing, the system should:
1. Parse the attribute header from each `.adoc` file
2. Extract `:categories:`, `:topics:`, `:extensions:`, `:summary:`, `:diataxis-type:`
3. Store this metadata in a dedicated `document_metadata` table linked to the `files` table
4. Expose metadata through a service interface so `SubjectDeriver`, `DocumentService`, and other consumers can query it

No API response changes — this is an internal data-layer feature.

### Data Volume Analysis

| Attribute | Docs with attribute | Example values |
|-----------|-------------------|----------------|
| `:categories:` | ~275 | `security,web`, `data`, `core`, `observability,cloud` |
| `:topics:` | ~259 | `rest,resteasy-reactive,virtual-threads`, `security,oidc` |
| `:extensions:` | ~250+ | `io.quarkus:quarkus-rest`, `io.quarkus:quarkus-hibernate-orm` |
| `:summary:` | ~250+ | One-line human-readable description |
| `:diataxis-type:` | ~65 | `reference`, `concept`, `tutorial`, `howto` |
| `:keywords:` | ~18 | Inconsistent format, low coverage — excluded from scope |

---

## Scope / Requirements

### R1: Extend `AsciidocParser` with Metadata Extraction

**File:** `src/main/java/com/fvd/asciidocs/parser/AsciidocParser.java`

Add a method to extract metadata attributes from AsciiDoc header blocks. The header block starts at line 1 and ends at the first blank line or the first section header (`==`).

```java
/**
 * Extracts document metadata attributes from the AsciiDoc header block.
 * Parses :categories:, :topics:, :extensions:, :summary:, and :diataxis-type:.
 *
 * @param content the full AsciiDoc content
 * @return extracted metadata (never null; fields may be empty lists or null)
 */
public DocumentMetadata extractMetadata(String content) {
    if (content == null || content.isBlank()) {
        return DocumentMetadata.empty();
    }
    // Parse header lines until first blank line or section header (==)
    // Extract :attribute: value patterns
    // Split comma-separated values for categories and topics
    // Return structured DocumentMetadata
}
```

Attribute patterns to match (all are colon-delimited):
```
:categories: security,web
:topics: security,oidc,authentication
:extensions: io.quarkus:quarkus-oidc
:summary: OIDC Authorization Code Flow mechanism for protecting web applications
:diataxis-type: reference
```

**Important:** The `:extensions:` attribute may contain colons in the GAV coordinates (e.g., `io.quarkus:quarkus-rest`). The regex must handle `:extensions: value` where `value` itself contains `:`. The value starts after the space following `:extensions:` and extends to end of line.

**Edge cases:**
- Multi-line attribute values (continuation with `\` at end of line) — not used in Quarkus docs, out of scope
- Multiple `:extensions:` values separated by commas: `io.quarkus:quarkus-rest,io.quarkus:quarkus-rest-jackson`
- Missing attributes: many quarkiverse docs have none of these attributes

### R2: Create `DocumentMetadata` Model

**New file:** `src/main/java/com/fvd/asciidocs/model/DocumentMetadata.java`

```java
package com.fvd.asciidocs.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Document metadata extracted from AsciiDoc header attributes.
 * Represents structured data from :categories:, :topics:, :extensions:,
 * :summary:, and :diataxis-type: attributes.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RegisterForReflection
public class DocumentMetadata {

    /** Comma-separated category tags (e.g., "security", "web", "data") */
    private List<String> categories;

    /** Comma-separated topic tags (e.g., "rest", "resteasy-reactive") */
    private List<String> topics;

    /** Extension GAV coordinates (e.g., "io.quarkus:quarkus-rest") */
    private List<String> extensions;

    /** Human-readable one-line description */
    private String summary;

    /** Diataxis documentation type: reference, concept, tutorial, howto */
    private String diataxisType;

    public static DocumentMetadata empty() {
        return DocumentMetadata.builder()
                .categories(List.of())
                .topics(List.of())
                .extensions(List.of())
                .build();
    }

    public boolean hasCategories() {
        return categories != null && !categories.isEmpty();
    }

    public boolean hasTopics() {
        return topics != null && !topics.isEmpty();
    }

    public boolean hasSummary() {
        return summary != null && !summary.isBlank();
    }
}
```

### R3: Add `extractMetadata` to `DocParser` Interface

**File:** `src/main/java/com/fvd/docs/parser/DocParser.java`

Add a default method so existing implementations don't break:

```java
/**
 * Extracts document metadata from document header attributes.
 * Default implementation returns empty metadata.
 *
 * @param content the full document content
 * @return extracted metadata (never null)
 */
default DocumentMetadata extractMetadata(String content) {
    return DocumentMetadata.empty();
}
```

### R4: Create `document_metadata` SQLite Table

**File:** `src/main/java/com/fvd/indexs/stores/SqliteSchemaInitializer.java`

Add a new table linked to `files` via foreign key:

```sql
CREATE TABLE IF NOT EXISTS document_metadata (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id INTEGER NOT NULL UNIQUE,
    categories TEXT,        -- comma-separated: "security,web"
    topics TEXT,            -- comma-separated: "security,oidc,authentication"
    extensions_gav TEXT,    -- comma-separated GAV: "io.quarkus:quarkus-oidc"
    summary TEXT,           -- human-readable description
    diataxis_type TEXT,     -- reference|concept|tutorial|howto
    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
);
```

Add index for efficient lookups:
```sql
CREATE INDEX IF NOT EXISTS idx_document_metadata_file_id ON document_metadata(file_id);
```

Update `resetSchema()` to drop the new table (add before `DROP TABLE IF EXISTS files`):
```sql
stmt.execute("DROP TABLE IF EXISTS document_metadata");
```

**Design choice:** Store categories and topics as comma-separated strings rather than normalized junction tables. Reasons:
- Simple reads (most queries need all categories/topics for a document)
- No need for relational queries across categories (that's what the `SubjectDeriver` does in application code)
- Consistent with how the source data is formatted
- Low cardinality (at most 5-6 categories per doc)

### R5: Create `DocumentMetadataStore` for Persistence

**New file:** `src/main/java/com/fvd/indexs/stores/DocumentMetadataStore.java`

```java
package com.fvd.indexs.stores;

import com.fvd.asciidocs.model.DocumentMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Slf4j
@ApplicationScoped
public class DocumentMetadataStore {

    @Inject
    DataSource dataSource;

    /**
     * Inserts metadata for a file. Called during indexing after the file is inserted.
     *
     * @param conn the active connection (shared with KeywordIndexStore transaction)
     * @param fileId the file ID from the files table
     * @param metadata the extracted metadata
     */
    public void insert(Connection conn, long fileId, DocumentMetadata metadata) throws SQLException {
        // INSERT INTO document_metadata (file_id, categories, topics, extensions_gav, summary, diataxis_type)
        // VALUES (?, ?, ?, ?, ?, ?)
    }

    /**
     * Reads metadata for a specific file path and version.
     *
     * @param version the documentation version
     * @param path the document file path
     * @return the metadata if found
     */
    public Optional<DocumentMetadata> readByPath(String version, String path) {
        // SELECT dm.* FROM document_metadata dm
        // JOIN files f ON dm.file_id = f.id
        // WHERE f.version = ? AND f.path = ?
    }

    /**
     * Reads metadata for all files of a version.
     *
     * @param version the documentation version
     * @return map of file path → metadata
     */
    public Map<String, DocumentMetadata> readAll(String version) {
        // SELECT f.path, dm.* FROM document_metadata dm
        // JOIN files f ON dm.file_id = f.id
        // WHERE f.version = ?
    }
}
```

### R6: Integrate Metadata Extraction into `KeywordIndexer`

**File:** `src/main/java/com/fvd/indexs/indexers/KeywordIndexer.java`

The `KeywordIndexer.build()` method already reads file content and inserts rows into the `files` table. After inserting each file, extract metadata and pass it to `DocumentMetadataStore.insert()`.

This requires modifying the `doInsert` flow in `KeywordIndexStore` to:
1. Insert the file row (existing)
2. Get the generated `file_id` (existing)
3. Extract metadata from content using `docParser.extractMetadata(content)` (new)
4. Insert metadata row via `DocumentMetadataStore.insert(conn, fileId, metadata)` (new)

**Approach:** The simplest integration point is in `KeywordIndexer.buildFileEntry()`, which already has access to the file content. Add a `DocumentMetadata metadata` field to `FileKeywordEntry` so the metadata travels through the existing `KeywordIndex` → `KeywordIndexStore.doInsert()` pipeline:

```java
// In FileKeywordEntry, add:
public DocumentMetadata metadata;

// In KeywordIndexer.buildFileEntry():
FileKeywordEntry entry = new FileKeywordEntry(filePath, fileScores, sectionEntries);
entry.metadata = parser.extractMetadata(content);
```

Then in `KeywordIndexStore.doInsert()`, after inserting the file row:
```java
if (file.metadata != null) {
    documentMetadataStore.insert(conn, fileId, file.metadata);
}
```

### R7: Expose Metadata Through a Service Method

**New file or enhancement:** `src/main/java/com/fvd/indexs/services/DocumentMetadataService.java` (optional — can also be accessed directly through `DocumentMetadataStore`)

Provide a simple service facade for consumers:

```java
/**
 * Get metadata for a specific document.
 */
public Optional<DocumentMetadata> getMetadata(String version, String path);

/**
 * Get all metadata for a version (batch operation for SubjectDeriver).
 */
public Map<String, DocumentMetadata> getAllMetadata(String version);
```

---

## Technical Design

### Metadata Extraction Regex

The AsciiDoc header block contains attribute definitions of the form `:name: value`. The parser must handle:

```
:categories: security,web
:topics: security,oidc,authentication,authorization
:extensions: io.quarkus:quarkus-oidc
:summary: OIDC Authorization Code Flow mechanism for protecting web applications
:diataxis-type: reference
```

Regex pattern for each attribute:
```java
private static final Pattern ATTR_CATEGORIES = Pattern.compile(
    "^:categories:\\s*(.+)$", Pattern.MULTILINE);
private static final Pattern ATTR_TOPICS = Pattern.compile(
    "^:topics:\\s*(.+)$", Pattern.MULTILINE);
private static final Pattern ATTR_EXTENSIONS = Pattern.compile(
    "^:extensions:\\s*(.+)$", Pattern.MULTILINE);
private static final Pattern ATTR_SUMMARY = Pattern.compile(
    "^:summary:\\s*(.+)$", Pattern.MULTILINE);
private static final Pattern ATTR_DIATAXIS = Pattern.compile(
    "^:diataxis-type:\\s*(.+)$", Pattern.MULTILINE);
```

**Optimization:** Only scan the header block (lines before the first blank line or `==` heading) to avoid false positives from document body content.

### Header Block Boundary

```java
private String extractHeaderBlock(String content) {
    StringBuilder header = new StringBuilder();
    for (String line : content.split("\n")) {
        if (line.isBlank() && header.length() > 0) {
            // Allow blank lines within the header only if followed by more attributes
            // AsciiDoc headers can have blank lines between attribute groups
            continue;
        }
        if (line.startsWith("== ")) {
            break; // First section header marks end of header block
        }
        header.append(line).append("\n");
    }
    return header.toString();
}
```

**Important note on header parsing**: In real Quarkus docs, the header block includes `:attribute: value` lines interspersed with `include::_attributes.adoc[]` directives and blank lines. The parser should scan from the start of the file until the first `== ` heading (level-2 section header) and extract attribute definitions from within that range.

### Comma-Separated Value Parsing

Categories and topics are comma-separated, sometimes with spaces:
```java
private List<String> parseCommaSeparated(String value) {
    if (value == null || value.isBlank()) {
        return List.of();
    }
    return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
}
```

### Transaction Integration

The metadata insert must share the same database connection and transaction as the file/keyword insert in `KeywordIndexStore.doInsert()`. This is achieved by passing the `Connection` object to `DocumentMetadataStore.insert(conn, fileId, metadata)`.

---

## Request/Response Examples

This feature does not change any API responses. The metadata is stored internally for use by Feature 76 (Subject Classification) and Feature 77 (Description Cleanup).

**Verification examples (internal/test only):**

Query the `document_metadata` table after indexing:
```sql
SELECT f.path, dm.categories, dm.topics, dm.extensions_gav, dm.summary, dm.diataxis_type
FROM document_metadata dm
JOIN files f ON dm.file_id = f.id
WHERE f.version = 'main' AND dm.categories IS NOT NULL
ORDER BY f.path
LIMIT 5;
```

Expected results:
```
security-oidc.adoc | security,web | security,oidc,authentication | io.quarkus:quarkus-oidc | OIDC Authentication... | reference
hibernate-orm.adoc | data | hibernate,orm,jpa | io.quarkus:quarkus-hibernate-orm | Hibernate ORM guide... | reference
rest-getting-started.adoc | web,getting-started | rest,resteasy-reactive | io.quarkus:quarkus-rest | Getting started with REST | tutorial
```

---

## Implementation Notes

### Header Block vs. Full Document Scan

Scanning only the header block (before the first `==` section header) is important for correctness. Some documents contain `:attribute:` patterns inside code blocks or prose that look like attribute definitions but aren't. Limiting the scan area prevents false positives.

### Quarkiverse Extension Docs

Quarkiverse docs use Antora structure and do NOT have `:categories:` or `:topics:` attributes. The metadata extraction will return `DocumentMetadata.empty()` for these files. This is correct and expected — Feature 76 will fall back to path-regex for quarkiverse docs.

### `FileKeywordEntry` Field Addition

Adding a `metadata` field to `FileKeywordEntry` is a backward-compatible change. The existing `KeywordIndex` serialization/deserialization is not affected because `DocumentMetadata` is only used during the write path. When reading from the database, metadata is loaded separately via `DocumentMetadataStore.readAll()`.

### Cascading Deletes

The `document_metadata` table uses `FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE`. When `KeywordIndexStore.doDelete()` runs `DELETE FROM files WHERE version = ?`, the metadata rows are automatically deleted. No additional cleanup is needed.

### Performance

Metadata extraction adds minimal overhead:
- One regex scan per file during indexing (header block only, ~20-50 lines)
- One additional SQL INSERT per file during indexing
- Metadata is loaded in bulk via `readAll()` when needed by downstream services

---

## Tasks

- [ ] Create `DocumentMetadata` model class in `com.fvd.asciidocs.model`
- [ ] Add `extractMetadata(String content)` default method to `DocParser` interface returning `DocumentMetadata.empty()`
- [ ] Implement `extractMetadata()` in `AsciidocParser` with regex patterns for `:categories:`, `:topics:`, `:extensions:`, `:summary:`, `:diataxis-type:`
- [ ] Add helper methods: `extractHeaderBlock()`, `parseCommaSeparated()`, `extractAttribute()`
- [ ] Add `document_metadata` table DDL to `SqliteSchemaInitializer.createTables()`
- [ ] Add `DROP TABLE IF EXISTS document_metadata` to `SqliteSchemaInitializer.resetSchema()` (before `DROP TABLE IF EXISTS files`)
- [ ] Add index `idx_document_metadata_file_id` on `document_metadata(file_id)`
- [ ] Create `DocumentMetadataStore` in `com.fvd.indexs.stores` with `insert()`, `readByPath()`, `readAll()` methods
- [ ] Add `DocumentMetadata metadata` field to `FileKeywordEntry`
- [ ] Populate `entry.metadata` in `KeywordIndexer.buildFileEntry()` by calling `parser.extractMetadata(content)`
- [ ] Inject `DocumentMetadataStore` into `KeywordIndexStore`
- [ ] Call `documentMetadataStore.insert(conn, fileId, file.metadata)` in `KeywordIndexStore.doInsert()` after file insertion
- [ ] Add unit tests for `AsciidocParser.extractMetadata()`:
    - Full header with all attributes → all fields populated
    - Header with only `:categories:` → only categories populated, others empty/null
    - No metadata attributes → `DocumentMetadata.empty()`
    - Multi-value categories: `:categories: security,web,data` → `["security", "web", "data"]`
    - Extension with colons: `:extensions: io.quarkus:quarkus-rest` → `["io.quarkus:quarkus-rest"]`
    - Multiple extensions: `:extensions: io.quarkus:quarkus-rest,io.quarkus:quarkus-rest-jackson` → two entries
    - Attributes inside code block body are NOT extracted
    - Blank/null content → `DocumentMetadata.empty()`
- [ ] Add unit tests for `DocumentMetadataStore`:
    - Insert and read back by path
    - Insert and read all for version
    - Read returns empty for non-existent path
    - Cascading delete when file is removed
- [ ] Add integration test verifying metadata is persisted during `KeywordIndexer.build()`
- [ ] Verify all existing tests pass — `FileKeywordEntry` field addition is backward compatible
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `AsciidocParser.extractMetadata(content)` correctly extracts `:categories: security,web` into `DocumentMetadata` with `categories = ["security", "web"]`
2. `AsciidocParser.extractMetadata(content)` correctly extracts `:topics: security,oidc,authentication` into `topics = ["security", "oidc", "authentication"]`
3. `AsciidocParser.extractMetadata(content)` correctly extracts `:extensions: io.quarkus:quarkus-oidc` into `extensions = ["io.quarkus:quarkus-oidc"]`
4. `AsciidocParser.extractMetadata(content)` correctly extracts `:summary:` and `:diataxis-type:` as plain strings
5. Content with no header attributes returns `DocumentMetadata.empty()` (empty lists, null strings)
6. The `document_metadata` table is created at startup alongside existing tables
7. After `KeywordIndexer.build()` completes, `DocumentMetadataStore.readAll(version)` returns metadata for every file that has metadata attributes
8. Files without metadata attributes (quarkiverse docs) have no row in `document_metadata` — `readByPath()` returns `Optional.empty()`
9. `resetSchema()` drops `document_metadata` before `files` to respect foreign key ordering
10. Cascading delete works: deleting a file row also deletes its metadata row
11. No API response changes — all existing endpoints return identical output
12. All existing tests pass unchanged
13. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Regex false positives: `:attribute:` patterns in code blocks or body text | Medium | Medium | Only scan header block (before first `==` heading); unit test with code block containing `:categories:` |
| `:extensions:` value contains colons, breaking simple regex | Medium | High | Use `^:extensions:\\s*(.+)$` which captures everything after the attribute name; the value itself is not parsed by regex |
| `FileKeywordEntry` field addition breaks deserialization | Low | High | `metadata` is not serialized to DB; it's a transient field used during the write path only. Existing `loadFileEntries()` does not read metadata from `files` table |
| Transaction boundary: metadata insert fails after file insert | Low | Medium | Both inserts share the same `Connection` within `doInsert()` which runs inside a transaction; rollback covers both |
| Quarkiverse docs have different header formats | Low | Low | `extractMetadata()` returns `DocumentMetadata.empty()` for unrecognized formats; quarkiverse fallback is handled by Feature 76 |
| Schema migration: existing databases lack `document_metadata` table | Low | Low | `CREATE TABLE IF NOT EXISTS` handles this; existing data is unaffected |
| Header block detection: some docs have unusual header structures | Low | Low | Scan until first `==` heading; this is robust for all Quarkus docs |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create `DocumentMetadata` model | 0.5 |
| Add `extractMetadata` to `DocParser` interface | 0.25 |
| Implement `extractMetadata` in `AsciidocParser` | 2.0 |
| Add `document_metadata` table to schema | 0.5 |
| Create `DocumentMetadataStore` | 2.0 |
| Integrate into `KeywordIndexer` + `KeywordIndexStore` | 1.5 |
| Unit tests for `AsciidocParser.extractMetadata()` | 1.5 |
| Unit tests for `DocumentMetadataStore` | 1.0 |
| Integration test for end-to-end indexing | 1.0 |
| Verify existing tests pass | 0.5 |
| **Total** | **~10.75 hours** |

---

## Files Modified

### New Production Files (2 files)
- `src/main/java/com/fvd/asciidocs/model/DocumentMetadata.java` — metadata model with categories, topics, extensions, summary, diataxisType
- `src/main/java/com/fvd/indexs/stores/DocumentMetadataStore.java` — SQLite persistence for document metadata

### Modified Production Files (5 files)
- `src/main/java/com/fvd/docs/parser/DocParser.java` — add `extractMetadata(String)` default method
- `src/main/java/com/fvd/asciidocs/parser/AsciidocParser.java` — implement `extractMetadata()` with regex extraction
- `src/main/java/com/fvd/indexs/stores/SqliteSchemaInitializer.java` — add `document_metadata` table DDL and drop in `resetSchema()`
- `src/main/java/com/fvd/indexs/indexers/FileKeywordEntry.java` — add `DocumentMetadata metadata` field
- `src/main/java/com/fvd/indexs/stores/KeywordIndexStore.java` — inject `DocumentMetadataStore`, call `insert()` during `doInsert()`

### New Test Files (3 files)
- `src/test/java/com/fvd/asciidocs/parser/AsciidocParserMetadataTest.java` — unit tests for metadata extraction
- `src/test/java/com/fvd/indexs/stores/DocumentMetadataStoreTest.java` — unit tests for metadata persistence
- `src/test/java/com/fvd/indexs/indexers/KeywordIndexerMetadataIntegrationTest.java` — integration test for end-to-end metadata indexing

---

## Dependencies

- **None** — this feature is self-contained and introduces new data structures without modifying existing API behavior.
- Downstream consumers: Feature 76 (Metadata-Based Subject Classification) and Feature 77 (AsciiDoc Description Cleanup) depend on this feature.

---

END OF FILE
