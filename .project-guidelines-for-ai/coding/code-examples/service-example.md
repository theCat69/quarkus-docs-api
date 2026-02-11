# Service Example

Reference for creating business logic / service classes in this project.

**Key patterns:**
- `@Slf4j` for logging via Lombok.
- `@ApplicationScoped` for CDI singleton lifecycle.
- `@RequiredArgsConstructor` for constructor-based dependency injection.
- Inject stores, configs, and other services via final fields.
- Use `ConcurrentHashMap` for in-memory caching.
- Return `List.of()` for empty results rather than `null`.
- Separate index loading/caching into private helper methods.

**Source:** `com.fvd.search.services.SearchService`

```java
package com.fvd.search.services;

import com.fvd.cache.services.CacheService;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.search.SearchConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SearchService {

    private final KeywordIndexStore keywordIndexStore;
    private final DocStore docStore;
    private final CacheService cacheService;
    private final SearchConfig searchConfig;

    private final Map<String, KeywordIndex> indexCache = new ConcurrentHashMap<>();

    public List<FileSearchResult> searchFiles(String version, List<String> keywords) {
        KeywordIndex index = getOrBuildIndex(version);
        if (index == null) {
            return List.of();
        }
        // ... scoring logic ...
        return List.of();
    }

    private KeywordIndex getOrBuildIndex(String version) {
        KeywordIndex cached = indexCache.get(version);
        if (cached != null) {
            return cached;
        }
        Optional<KeywordIndex> index = keywordIndexStore.read(version);
        if (index.isPresent()) {
            indexCache.put(version, index.get());
            return index.get();
        }
        return null;
    }
}
```
