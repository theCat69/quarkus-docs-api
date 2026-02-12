# Feature 40: Repository Abstraction

## Summary

Version 1.1.1 introduces a repository abstraction layer that decouples the application from SQLite-specific implementations. This prepares the codebase for PostgreSQL migration in v1.2.0 while maintaining current SQLite functionality.

## User Story

**As a** developer  
**I want** database operations abstracted behind interfaces  
**So that** we can switch from SQLite to PostgreSQL without changing business logic

## Motivation

The current implementation has SQLite-specific code intertwined with business logic. Abstracting to a repository pattern enables:
- Database-agnostic business logic
- Easier testing with mock repositories
- Clean migration path to PostgreSQL FTS
- Potential for other storage backends

---

## Requirements

### Repository Interfaces

#### KeywordIndexRepository

```java
public interface KeywordIndexRepository {
    
    /**
     * Check if an index exists for the given version.
     */
    boolean exists(String version);
    
    /**
     * Find the keyword index for a version.
     * @return Optional containing index data, or empty if not found
     */
    Optional<KeywordIndexData> findByVersion(String version);
    
    /**
     * Save keyword index data for a version.
     * Replaces existing data if present.
     */
    void save(String version, KeywordIndexData data);
    
    /**
     * Delete keyword index for a version.
     */
    void deleteByVersion(String version);
}
```

#### CodeSampleIndexRepository

```java
public interface CodeSampleIndexRepository {
    
    /**
     * Check if an index exists for the given version.
     */
    boolean exists(String version);
    
    /**
     * Find the code sample index for a version.
     */
    Optional<CodeSampleIndexData> findByVersion(String version);
    
    /**
     * Save code sample index data for a version.
     */
    void save(String version, CodeSampleIndexData data);
    
    /**
     * Delete code sample index for a version.
     */
    void deleteByVersion(String version);
}
```

#### GithubIndexRepository

```java
public interface GithubIndexRepository {
    
    /**
     * Check if GitHub file index exists for version.
     */
    boolean exists(String version);
    
    /**
     * Find GitHub file entries for a version.
     */
    Optional<List<GithubFileEntry>> findByVersion(String version);
    
    /**
     * Save GitHub file entries for a version.
     */
    void save(String version, List<GithubFileEntry> entries);
    
    /**
     * Delete GitHub file index for a version.
     */
    void deleteByVersion(String version);
}
```

#### SearchRepository

```java
public interface SearchRepository {
    
    /**
     * Search files by query criteria.
     */
    SearchResult<FileMatch> searchFiles(FileSearchQuery query);
    
    /**
     * Search sections within files.
     */
    SearchResult<SectionMatch> searchSections(SectionSearchQuery query);
    
    /**
     * Search code samples.
     */
    SearchResult<CodeSampleMatch> searchCodeSamples(CodeSampleSearchQuery query);
    
    /**
     * Invalidate search cache for a version.
     */
    void invalidateCache(String version);
}
```

#### SchemaInitializer

```java
public interface SchemaInitializer {
    
    /**
     * Initialize database schema.
     * Creates tables if they don't exist.
     */
    void initSchema();
    
    /**
     * Reset database schema.
     * Drops and recreates all tables.
     */
    void resetSchema();
}
```

---

### Domain Objects

#### KeywordIndexData

```java
@RegisterForReflection
public record KeywordIndexData(
    String version,
    Instant indexedAt,
    List<FileEntry> files
) {
    
    public record FileEntry(
        String path,
        String title,
        String description,
        String subject,
        String extension,
        List<SectionEntry> sections,
        List<KeywordWeight> keywords
    ) {}
    
    public record SectionEntry(
        String title,
        int level,
        int startLine,
        int endLine
    ) {}
    
    public record KeywordWeight(
        String keyword,
        String stemmed,
        String source,      // filename, title, section, subtitle, body
        double weight,
        int frequency,
        Integer lineNumber  // null for filename/title
    ) {}
}

/**
 * Matched keyword with metadata for API responses.
 */
@RegisterForReflection
public record MatchedKeyword(
    String keyword,
    String source,    // filename, title, section, subtitle, body
    double weight
) {}
```

#### CodeSampleIndexData

```java
@RegisterForReflection
public record CodeSampleIndexData(
    String version,
    Instant indexedAt,
    List<CodeSampleEntry> samples
) {
    
    public record CodeSampleEntry(
        String id,
        String documentPath,
        String documentTitle,
        String subject,
        String extension,
        String language,
        String content,
        String context,
        int startLine,
        int endLine,
        List<String> keywords
    ) {}
}
```

#### GithubFileEntry

```java
@RegisterForReflection
public record GithubFileEntry(
    String path,
    String sha,
    long size,
    String downloadUrl,
    Instant fetchedAt
) {}
```

#### Search Query Objects

```java
@RegisterForReflection
public record FileSearchQuery(
    String version,
    List<String> keywords,
    String subject,
    String extension,
    int limit,
    int offset
) {
    public static FileSearchQuery of(String version, List<String> keywords) {
        return new FileSearchQuery(version, keywords, null, null, 20, 0);
    }
}

@RegisterForReflection
public record SectionSearchQuery(
    String version,
    List<String> keywords,
    String subject,
    String extension,
    String documentPath,
    int limit,
    int offset
) {}

@RegisterForReflection
public record CodeSampleSearchQuery(
    String version,
    List<String> keywords,
    String language,
    String subject,
    String extension,
    int limit,
    int offset
) {}
```

#### Search Result Objects

```java
@RegisterForReflection
public record SearchResult<T>(
    List<T> items,
    int totalCount,
    int returnedCount,
    boolean hasMore
) {
    public static <T> SearchResult<T> empty() {
        return new SearchResult<>(List.of(), 0, 0, false);
    }
    
    public static <T> SearchResult<T> of(List<T> items, int totalCount) {
        return new SearchResult<>(items, totalCount, items.size(), items.size() < totalCount);
    }
}

@RegisterForReflection
public record FileMatch(
    String path,
    String title,
    String description,
    String subject,
    String extension,
    double score,
    List<MatchedKeyword> matchedKeywords,
    String snippet
) {}

@RegisterForReflection
public record SectionMatch(
    String documentPath,
    String documentTitle,
    String sectionTitle,
    int level,
    String content,
    int startLine,
    int endLine,
    double score,
    List<MatchedKeyword> matchedKeywords
) {}

@RegisterForReflection
public record CodeSampleMatch(
    String id,
    String documentPath,
    String documentTitle,
    String subject,
    String extension,
    String language,
    String content,
    String context,
    int startLine,
    int endLine,
    double score,
    List<MatchedKeyword> matchedKeywords
) {}
```

---

### Dependency Injection Strategy

#### Configuration

```properties
# application.properties

# Database type: sqlite (default) or postgresql
app.database.type=sqlite

# SQLite configuration
app.database.sqlite.path=./data/quarkus-docs.db

# PostgreSQL configuration (for 1.2.0)
# quarkus.datasource.db-kind=postgresql
# quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/quarkus_docs
```

#### Bean Selection with @LookupIfProperty

```java
// SQLite Implementation
@ApplicationScoped
@LookupIfProperty(name = "app.database.type", stringValue = "sqlite", lookupIfMissing = true)
public class SqliteKeywordIndexRepository implements KeywordIndexRepository {
    // SQLite-specific implementation
}

// PostgreSQL Implementation (1.2.0)
@ApplicationScoped
@LookupIfProperty(name = "app.database.type", stringValue = "postgresql")
public class PostgresKeywordIndexRepository implements KeywordIndexRepository {
    // PostgreSQL-specific implementation with tsvector
}
```

#### Interface Injection

```java
@ApplicationScoped
public class DocumentService {
    
    @Inject
    KeywordIndexRepository keywordIndexRepository;
    
    @Inject
    SearchRepository searchRepository;
    
    // Business logic uses interfaces only
    public SearchResult<FileMatch> search(String version, List<String> keywords) {
        var query = FileSearchQuery.of(version, keywords);
        return searchRepository.searchFiles(query);
    }
}
```

---

## Implementation Notes

### SQLite Implementation Characteristics

Current SQLite implementation:
- In-memory search with custom Porter Stemmer
- JSON blob storage for index data
- Manual FTS using LIKE queries
- Custom scoring calculation

```java
@ApplicationScoped
@LookupIfProperty(name = "app.database.type", stringValue = "sqlite", lookupIfMissing = true)
public class SqliteSearchRepository implements SearchRepository {
    
    @Inject
    Stemmer stemmer;
    
    @Override
    public SearchResult<FileMatch> searchFiles(FileSearchQuery query) {
        // Load index from SQLite
        // Stem keywords
        // In-memory matching with scoring
        // Sort and paginate
    }
}
```

### PostgreSQL Implementation (1.2.0 Preview)

PostgreSQL implementation will use:
- Native FTS with `tsvector` and `ts_query`
- `ts_rank` for scoring
- GIN indexes for performance
- Built-in stemming dictionaries

```java
@ApplicationScoped
@LookupIfProperty(name = "app.database.type", stringValue = "postgresql")
public class PostgresSearchRepository implements SearchRepository {
    
    @Inject
    DataSource dataSource;
    
    @Override
    public SearchResult<FileMatch> searchFiles(FileSearchQuery query) {
        // Build ts_query from keywords
        // Execute FTS query with ts_rank
        // Map results to FileMatch
    }
}
```

### Stemmer Differences

| Aspect | SQLite (Custom) | PostgreSQL (english) |
|--------|-----------------|----------------------|
| Algorithm | Porter Stemmer | Snowball |
| Behavior | `running` → `run` | `running` → `run` |
| Edge cases | May differ slightly | Standard behavior |

**Accepted Trade-off:** Minor stemming differences between implementations are acceptable. Document behavior, don't guarantee identical results.

---

## Schema Initialization

### SQLite Schema

The repository abstraction works with the existing normalized schema. The following migrations extend existing tables:

```sql
-- Migration: Add subject and description columns to files table
ALTER TABLE files ADD COLUMN subject TEXT;
ALTER TABLE files ADD COLUMN description TEXT;

-- Migration: Add subject column to code_samples table
ALTER TABLE code_samples ADD COLUMN subject TEXT;

-- Migration: Add source column to file_keywords for keyword scoring
ALTER TABLE file_keywords ADD COLUMN source TEXT NOT NULL DEFAULT 'body';

-- Migration: Add source column to section_keywords for keyword scoring
ALTER TABLE section_keywords ADD COLUMN source TEXT NOT NULL DEFAULT 'body';

-- Indexes for efficient filtering
CREATE INDEX IF NOT EXISTS idx_files_subject ON files(subject);
CREATE INDEX IF NOT EXISTS idx_files_version ON files(version);
CREATE INDEX IF NOT EXISTS idx_code_samples_subject ON code_samples(subject);
CREATE INDEX IF NOT EXISTS idx_file_keywords_source ON file_keywords(source);
CREATE INDEX IF NOT EXISTS idx_section_keywords_source ON section_keywords(source);
```

**Note:** The SQLite implementation uses the existing normalized schema (files, sections, file_keywords, section_keywords, code_samples tables). Repository interfaces abstract over this schema, enabling future PostgreSQL migration without changing business logic.

### PostgreSQL Schema (1.2.0 Preview)

```sql
-- Keyword Index with FTS
CREATE TABLE IF NOT EXISTS keyword_index (
    id SERIAL PRIMARY KEY,
    version TEXT NOT NULL,
    file_path TEXT NOT NULL,
    title TEXT,
    description TEXT,
    subject TEXT,
    extension TEXT,
    content_tsv TSVECTOR,
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(version, file_path)
);

CREATE INDEX idx_keyword_fts ON keyword_index USING GIN(content_tsv);
CREATE INDEX idx_keyword_version ON keyword_index(version);
CREATE INDEX idx_keyword_subject ON keyword_index(subject);

-- Similar for code samples with code-specific FTS
```

---

## Migration Path

### Phase 1: v1.1.1 (Current)
- Implement repository interfaces
- SQLite implementation as default
- All business logic uses interfaces

### Phase 2: v1.2.0 (Future)
- Add PostgreSQL implementations
- Configuration switch for database type
- Migration tooling for index data
- Performance benchmarking

### Phase 3: v1.3.0+ (Future)
- Deprecate SQLite for production
- PostgreSQL optimizations
- Consider removing SQLite support

---

## Acceptance Criteria

### Repository Interfaces
- [x] All 5 interfaces defined with Javadoc
- [x] Domain records defined as specified
- [x] Interfaces in dedicated package (`repository.api`)

### SQLite Implementation
- [x] All interfaces implemented for SQLite
- [x] Existing behavior preserved
- [x] `@LookupIfProperty` annotation with `lookupIfMissing = true`

### DI Configuration
- [x] `app.database.type` property defined
- [x] Default is `sqlite`
- [x] Bean selection works correctly

### Testing
- [x] Integration tests pass with SQLite
- [x] Mock repositories work for unit tests
- [x] Configuration switching tested

---

## Dependencies

- Quarkus ARC (CDI)
- SmallRye Config
- Jackson (JSON serialization)
- SQLite JDBC
- (Future) Quarkus JDBC PostgreSQL

## Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Interface design lock-in | Hard to change later | Review interface design carefully |
| Performance regression | Slower than direct access | Benchmark, optimize hot paths |
| Stemmer differences | Different search results | Document, accept minor differences |
| Configuration complexity | User confusion | Clear defaults, good documentation |

---

## Implementation Notes

**Implemented:** Thu Feb 12 2026

**Files Created:**
- `com.fvd.repository.api/` - 5 repository interfaces (KeywordIndexRepository, CodeSampleIndexRepository, GithubIndexRepository, SearchRepository, SchemaInitializer)
- `com.fvd.repository.domain/` - 15 domain records
- `com.fvd.repository.sqlite/` - SQLite implementations with @LookupIfProperty

**Configuration:**
- Added `app.database.type=sqlite` to application.properties

**Review Status:**
- Code Review: PASS
- Security Review: PASS
- All tests passing
