# Feature 44: Search Abstraction & Scorer Interface

## Summary

Introduce abstraction layers for search scoring and store operations to support the upcoming PostgreSQL migration. This feature creates a `SearchScorer` interface that abstracts the scoring mechanism (allowing SQLite custom matching and PostgreSQL `ts_rank()` implementations), consolidates duplicate store patterns into an abstract base class, and extracts common indexer logic.

## User Story

**As a** maintainer of the quarkus-docs-api  
**I want** search scoring and persistence operations abstracted behind well-defined interfaces  
**So that** I can seamlessly migrate from SQLite (custom keyword matching) to PostgreSQL (full-text search with `ts_rank()`) without breaking existing functionality.

## Motivation

1. **PostgreSQL Migration**: The current `SearchService.computeMatchingScore()` uses manual exact/prefix matching that cannot work with PostgreSQL's `ts_rank()` function.
2. **Code Duplication**: Three store classes duplicate the transactional `write()` pattern and `exists()` checks.
3. **Indexer Duplication**: Both indexers have nearly identical `build()` overloads and `toSortedScores()` methods.
4. **Testability**: Interface-based design enables easy mocking.

---

## Requirements

### R1: SearchScorer Interface

```java
package com.fvd.search.services;

import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.repository.domain.MatchedKeyword;
import java.util.List;
import java.util.Set;

/**
 * Abstracts search scoring to support multiple backend implementations.
 * - SQLite: Custom exact/prefix matching with configurable multipliers.
 * - PostgreSQL: Delegates to ts_rank() via native queries.
 */
public interface SearchScorer {

    MatchResult computeScore(List<KeywordScore> indexedKeywords, Set<String> queryKeywords);

    record MatchResult(double score, int matchedCount, List<MatchedKeyword> matchedKeywords) {
        public static final MatchResult EMPTY = new MatchResult(0.0, 0, List.of());
        public boolean hasMatches() { return matchedCount > 0; }
    }
}
```

### R2: SQLite SearchScorer Implementation

```java
package com.fvd.search.services;

import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.repository.domain.MatchedKeyword;
import com.fvd.search.SearchConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import java.util.*;

@ApplicationScoped
@RequiredArgsConstructor
public class SqliteSearchScorer implements SearchScorer {

    private final SearchConfig searchConfig;

    @Override
    public MatchResult computeScore(List<KeywordScore> indexedKeywords, Set<String> queryKeywords) {
        double prefixMultiplier = searchConfig.boost().prefixMatchMultiplier();
        double totalScore = 0;
        Map<String, MatchedKeyword> matchedByQuery = new HashMap<>();

        for (KeywordScore ks : indexedKeywords) {
            double bestScore = 0;
            String bestQueryKeyword = null;

            for (String query : queryKeywords) {
                if (ks.word.equals(query)) {
                    bestScore = ks.score;
                    bestQueryKeyword = query;
                    break;
                } else if (ks.word.startsWith(query)) {
                    double prefixScore = ks.score * prefixMultiplier;
                    if (prefixScore > bestScore) {
                        bestScore = prefixScore;
                        bestQueryKeyword = query;
                    }
                }
            }

            if (bestQueryKeyword != null) {
                totalScore += bestScore;
                String source = ks.source != null ? ks.source : "body";
                MatchedKeyword existing = matchedByQuery.get(bestQueryKeyword);
                if (existing == null || bestScore > existing.weight()) {
                    matchedByQuery.put(bestQueryKeyword, 
                        new MatchedKeyword(bestQueryKeyword, source, bestScore));
                }
            }
        }

        return new MatchResult(totalScore, matchedByQuery.size(), List.copyOf(matchedByQuery.values()));
    }
}
```

### R3: Abstract Versioned Store Base Class

```java
package com.fvd.indexs.stores;

import com.fvd.common.validators.InputValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractVersionedStore<T> {

    protected final DataSource dataSource;

    public boolean exists(String version) {
        InputValidator.validateVersion(version);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(existsQuery())) {
            stmt.setString(1, version);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check " + indexName() + " existence", e);
        }
    }

    public void write(String version, T index) {
        InputValidator.validateVersion(version);
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                doDelete(conn, version);
                doInsert(conn, version, index);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to write " + indexName(), e);
        }
    }

    protected abstract String indexName();
    protected abstract String existsQuery();
    protected abstract Optional<T> doRead(Connection conn, String version) throws SQLException;
    protected abstract void doDelete(Connection conn, String version) throws SQLException;
    protected abstract void doInsert(Connection conn, String version, T index) throws SQLException;
}
```

### R4: Keyword Preparation Utility

```java
package com.fvd.search.services;

import com.fvd.common.Stemmer;
import lombok.experimental.UtilityClass;
import java.util.*;

@UtilityClass
public class SearchKeywords {

    public Set<String> prepare(List<String> keywords) {
        return new HashSet<>(keywords.stream()
            .map(k -> Stemmer.stem(k.toLowerCase()))
            .toList());
    }
}
```

### R5: Shared Score Sorting Utility

```java
package com.fvd.indexs.indexers;

import lombok.experimental.UtilityClass;
import java.util.*;

@UtilityClass
public class KeywordScoreUtils {

    public List<KeywordScore> toSortedScores(Map<String, Integer> keywords) {
        return keywords.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .map(e -> new KeywordScore(e.getKey(), e.getValue()))
            .toList();
    }
}
```

---

## Tasks

- [ ] Create `SearchScorer` interface with `MatchResult` record
- [ ] Implement `SqliteSearchScorer` 
- [ ] Refactor `SearchService` to use injected `SearchScorer`
- [ ] Create `AbstractVersionedStore<T>` base class
- [ ] Refactor `KeywordIndexStore` to extend base class
- [ ] Refactor `CodeSampleIndexStore` to extend base class
- [ ] Create `SearchKeywords` utility
- [ ] Create `KeywordScoreUtils` utility
- [ ] Move `applyFilenameBoost()` to `KeywordScorer`
- [ ] Write unit tests for all new classes

---

## Acceptance Criteria

- [ ] `SearchScorer` interface exists with `computeScore()` and `MatchResult`
- [ ] `SqliteSearchScorer` is `@ApplicationScoped` and passes all existing search tests
- [ ] `SearchService` uses injected `SearchScorer`
- [ ] `AbstractVersionedStore` eliminates duplicate transactional patterns
- [ ] `KeywordIndexStore` and `CodeSampleIndexStore` extend base class
- [ ] `SearchKeywords.prepare()` replaces duplicate stemming patterns
- [ ] `KeywordScoreUtils.toSortedScores()` used by both indexers
- [ ] All existing tests pass
- [ ] New unit tests achieve >90% coverage

---

## Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Interface doesn't fit PostgreSQL `ts_rank()` | Medium | High | Design based on PostgreSQL docs |
| Breaking existing search behavior | Low | High | Comprehensive test suite |
| Performance regression | Low | Medium | Benchmark before/after |

---

END OF FILE
