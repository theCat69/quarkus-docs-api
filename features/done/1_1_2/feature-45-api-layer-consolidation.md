# Feature 45: API Layer Consolidation

## Summary

Consolidate duplicated patterns in the API layer including paginated response DTOs, document title extraction, search constants, and input validation parameters.

## User Story

**As a** maintainer  
**I want** API layer duplications consolidated  
**So that** adding new endpoints or modifying behavior requires changes in one place only

## Motivation

Static analysis identified duplications across API resources and services:
- 3 paginated response DTOs with identical structure
- 3 services with identical `getDocumentTitle()` methods
- 3 resources with identical `DEFAULT_LIMIT`/`MAX_LIMIT` constants
- 3 resources with identical 4-line validation blocks

---

## Requirements

### 1. Generic Paginated Response Base Class

```java
package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@RegisterForReflection
public class PaginatedResponse<T> {
    protected List<T> results;
    protected int totalCount;
    protected int returnedCount;
    
    public static <T> PaginatedResponse<T> of(List<T> results, int total) {
        return PaginatedResponse.<T>builder()
                .results(results)
                .totalCount(total)
                .returnedCount(results.size())
                .build();
    }
}
```

**Note**: Using `@Data` + `@SuperBuilder` instead of `@Value` because `@Value` makes classes final 
and cannot be extended. Subclasses like `QuickSearchResponse` need to extend this base class.

### 2. Document Title Extractor Utility

```java
package com.fvd.common.utils;

import lombok.experimental.UtilityClass;
import java.util.regex.*;

@UtilityClass
public class DocumentTitleExtractor {
    
    private static final Pattern TITLE_PATTERN = 
        Pattern.compile("^=\\s+(.+)$", Pattern.MULTILINE);
    
    public static String extractTitle(String content) {
        if (content == null || content.isBlank()) return "";
        Matcher matcher = TITLE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1).trim() : "";
    }
}
```

### 3. Search Constants Class

```java
package com.fvd.common;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SearchConstants {
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;
    public static final int DEFAULT_OFFSET = 0;
    public static final int SNIPPET_CONTEXT_SIZE = 80;
}
```

### 4. Search Parameters Record

```java
package com.fvd.api.dto;

import com.fvd.common.SearchConstants;
import com.fvd.common.validators.InputValidator;
import lombok.Builder;
import java.util.List;

@Builder
public record SearchParams(
    String version,
    List<String> keywords,
    String subject,
    String extension,
    int limit,
    int offset
) {
    public static SearchParams fromRaw(
            String version, String keywords, String subject,
            String extension, Integer limit, Integer offset) {
        return SearchParams.builder()
                .version(InputValidator.resolveVersion(version))
                .keywords(InputValidator.parseKeywords(keywords))
                .subject(normalizeFilter(subject))
                .extension(normalizeFilter(extension))
                .limit(InputValidator.validateLimit(limit, SearchConstants.DEFAULT_LIMIT, SearchConstants.MAX_LIMIT))
                .offset(InputValidator.validateOffset(offset))
                .build();
    }
    
    private static String normalizeFilter(String filter) {
        return (filter == null || filter.isBlank()) ? null : filter.trim();
    }
}
```

### 5. Filter Utilities

```java
package com.fvd.common.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class FilterUtils {
    public static boolean matchesFilter(String filter, String value) {
        return filter == null || filter.isBlank() || filter.equals(value);
    }
}
```

---

## Tasks

- [x] Create `PaginatedResponse<T>` generic class
- [x] Migrate existing response DTOs to extend base
- [x] Create `DocumentTitleExtractor` utility
- [x] Remove `TITLE_PATTERN` from 3 services, use utility
- [x] Create `SearchConstants` class
- [x] Remove duplicate constants from resources
- [x] Create `SearchParams` record
- [x] Refactor resources to use `SearchParams.fromRaw()`
- [x] Create `FilterUtils` utility
- [x] Simplify subject/extension filtering in services
- [x] Write unit tests

---

## Acceptance Criteria

- [x] `PaginatedResponse<T>` exists with factory method
- [x] All paginated DTOs use base class
- [x] `DocumentTitleExtractor` eliminates 3 duplicate methods
- [x] `SearchConstants` centralizes all limit/offset defaults
- [x] `SearchParams.fromRaw()` centralizes validation logic
- [x] `FilterUtils.matchesFilter()` simplifies filtering
- [x] OpenAPI spec unchanged
- [x] All tests pass

---

## Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Lombok `@SuperBuilder` complexity | Low | Use manual constructors if needed |
| Service signature changes | Medium | Keep signatures initially, refactor gradually |
| Lombok `@SuperBuilder` requires all hierarchy classes to use it | Low | All subclasses must also use `@SuperBuilder` annotation |

---

END OF FILE
