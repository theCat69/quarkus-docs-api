# Feature 43: Repository Layer Transaction Template Refactoring

> **Dependencies**: None. This is a standalone refactoring that prepares the codebase for PostgreSQL migration.

## Summary

Extract duplicated database operation patterns from SQLite repository implementations into reusable abstractions. This includes a `TransactionTemplate` for safe transaction handling, common `existsByVersion()` and `deleteByVersion()` base methods, and a shared `SqlUtils` utility class for SQL helpers like placeholder building.

## User Story

As a **developer**, I want to eliminate duplicated database patterns in the repository layer so that:
- Transaction handling is consistent and less error-prone
- Future PostgreSQL migration requires changes in fewer places
- New repositories don't require copy-pasting boilerplate code
- Bug fixes to transaction handling apply everywhere automatically

## Motivation

The current SQLite repository implementations contain significant code duplication:

1. **Transaction handling** (HIGH priority): The exact same try-catch-rollback-commit pattern appears in 3 repository classes (12-15 lines each = ~40 lines duplicated)
2. **exists() method** (HIGH priority): Identical `SELECT 1 FROM {table} WHERE version = ? LIMIT 1` pattern in 3 classes
3. **deleteByVersion()** (MEDIUM priority): Same validation + delete delegation pattern in 3 classes
4. **Placeholder building** (MEDIUM priority): `buildPlaceholders()` utility buried in `SqliteSearchRepository`

This duplication:
- Increases maintenance burden (fix in 3+ places)
- Creates risk of inconsistent behavior during PostgreSQL migration
- Makes the codebase harder to understand

---

## Requirements

### 1. TransactionTemplate Functional Interface

Create a functional interface and helper for executing operations within a transaction:

```java
package com.fvd.repository.sqlite;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Functional interface for database operations requiring a transaction.
 *
 * @param <T> the return type of the operation (use Void for no return)
 */
@FunctionalInterface
public interface TransactionalOperation<T> {
    T execute(Connection conn) throws SQLException;
}
```

```java
package com.fvd.repository.sqlite;

import com.fvd.repository.exceptions.RepositoryException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Template for executing database operations within a transaction.
 * Handles connection management, commit, and rollback automatically.
 */
@Slf4j
@UtilityClass
public class TransactionTemplate {

    /**
     * Executes an operation within a transaction with automatic rollback on failure.
     */
    public static <T> T executeInTransaction(
            DataSource dataSource,
            TransactionalOperation<T> operation,
            String errorMessage) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = operation.execute(conn);
                conn.commit();
                return result;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RepositoryException(errorMessage, e);
        }
    }

    /**
     * Executes a void operation within a transaction.
     */
    public static void executeInTransactionVoid(
            DataSource dataSource,
            VoidTransactionalOperation operation,
            String errorMessage) {
        executeInTransaction(dataSource, conn -> {
            operation.execute(conn);
            return null;
        }, errorMessage);
    }
}
```

**Usage example** (refactored `save()` method):

```java
// BEFORE (SqliteKeywordIndexRepository:80-94)
@Override
public void save(String version, KeywordIndexData data) {
    InputValidator.validateVersion(version);
    Objects.requireNonNull(data, "data must not be null");

    try (Connection conn = dataSource.getConnection()) {
        conn.setAutoCommit(false);
        try {
            deleteVersion(conn, version);
            insertIndex(conn, version, data);
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    } catch (SQLException e) {
        throw new RepositoryException("Failed to write keyword index for version: " + version, e);
    }
}

// AFTER
@Override
public void save(String version, KeywordIndexData data) {
    InputValidator.validateVersion(version);
    Objects.requireNonNull(data, "data must not be null");

    TransactionTemplate.executeInTransactionVoid(dataSource, conn -> {
        deleteVersion(conn, version);
        insertIndex(conn, version, data);
    }, "Failed to write keyword index for version: " + version);
}
```

### 2. SqlUtils Utility Class

Create a shared utility class for common SQL operations:

```java
package com.fvd.repository.sqlite;

import lombok.experimental.UtilityClass;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.StringJoiner;

/**
 * Utility methods for common SQL operations.
 */
@UtilityClass
public class SqlUtils {

    /**
     * Builds a comma-separated list of SQL placeholders.
     */
    public static String buildPlaceholders(int count) {
        if (count <= 0) return "";
        StringJoiner joiner = new StringJoiner(", ");
        for (int i = 0; i < count; i++) {
            joiner.add("?");
        }
        return joiner.toString();
    }

    /**
     * Checks if any row exists for a given version in a table.
     */
    public static boolean existsByVersion(
            Connection conn, String tableName, String versionColumn, String version) 
            throws SQLException {
        String sql = "SELECT 1 FROM " + tableName + " WHERE " + versionColumn + " = ? LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, version);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Deletes all rows for a given version from a table.
     */
    public static int deleteByVersion(
            Connection conn, String tableName, String versionColumn, String version) 
            throws SQLException {
        String sql = "DELETE FROM " + tableName + " WHERE " + versionColumn + " = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, version);
            return stmt.executeUpdate();
        }
    }
}
```

### 3. Repository Refactoring

Each repository implementation should be updated to use the new utilities:

**SqliteKeywordIndexRepository** changes:
- `exists()` → use `SqlUtils.existsByVersion()`
- `save()` → use `TransactionTemplate.executeInTransactionVoid()`
- `deleteByVersion()` → simplify with `SqlUtils.deleteByVersion()`

**SqliteGithubIndexRepository** changes:
- `exists()` → use `SqlUtils.existsByVersion()`
- `save()` → use `TransactionTemplate.executeInTransactionVoid()`
- `deleteByVersion()` → simplify with `SqlUtils.deleteByVersion()`

**SqliteCodeSampleIndexRepository** changes:
- `exists()` → use `SqlUtils.existsByVersion()`
- `save()` → use `TransactionTemplate.executeInTransactionVoid()`
- `deleteByVersion()` → simplify with `SqlUtils.deleteByVersion()`

**SqliteSearchRepository** changes:
- Remove private `buildPlaceholders()` method
- Import and use `SqlUtils.buildPlaceholders()`

---

## Implementation Notes

### Package Location
All new classes in `com.fvd.repository.sqlite` since they are SQLite-specific.

### Table Name Safety
Table names are **hardcoded constants**, NOT user input. Document clearly in Javadoc.

### Lombok Usage
- Use `@UtilityClass` for `TransactionTemplate` and `SqlUtils`
- Use `@Slf4j` for logging in `TransactionTemplate`

### Lines of Code Impact
**Before**: ~107 lines of duplication  
**After**: ~105 lines total, but **single source of truth**

---

## Tasks

- [ ] Create `TransactionalOperation.java` functional interface
- [ ] Create `VoidTransactionalOperation.java` functional interface
- [ ] Create `TransactionTemplate.java` utility class
- [ ] Create unit test `TransactionTemplateTest.java`
- [ ] Create `SqlUtils.java` utility class
- [ ] Create unit test `SqlUtilsTest.java`
- [ ] Refactor `SqliteKeywordIndexRepository` to use utilities
- [ ] Refactor `SqliteGithubIndexRepository` to use utilities
- [ ] Refactor `SqliteCodeSampleIndexRepository` to use utilities
- [ ] Refactor `SqliteSearchRepository` to use `SqlUtils.buildPlaceholders()`
- [ ] Run all tests (`./gradlew test`) — all must pass

---

## Acceptance Criteria

1. `TransactionTemplate.executeInTransaction()` and `executeInTransactionVoid()` exist
2. `SqlUtils.buildPlaceholders()`, `existsByVersion()`, `deleteByVersion()` exist
3. All three index repositories use `TransactionTemplate` for `save()` methods
4. All three index repositories use `SqlUtils` methods
5. `SqliteSearchRepository` uses `SqlUtils.buildPlaceholders()`
6. All existing tests pass without modification
7. New unit tests exist with >90% coverage

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Transaction rollback behavior differs | Low | High | Comprehensive testing |
| Table name injection | Very Low | Critical | Hardcoded constants only |
| Existing tests break | Low | Medium | Run full test suite after each refactor |

---

END OF FILE
