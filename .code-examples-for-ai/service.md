# Pattern: Application Service (business logic / orchestration layer)
# Demonstrates: @ApplicationScoped, @RequiredArgsConstructor, @Slf4j, @ConfigProperty,
# Optional handling, private records as value carriers, in-memory LRU cache, and
# delegation to stores.

```java
package com.fvd.api.services;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fvd.api.dto.DocumentResponse;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.stores.DocChunkStore;

// @Slf4j generates: private static final Logger log = ...
@Slf4j
@ApplicationScoped                               // Default CDI scope for stateless/stateful services
@RequiredArgsConstructor                         // Constructor injection — no @Inject on fields
public class DocumentService {

    // Constants are static final — extracted for readability
    private static final int FULL_CONTENT_MAX_LIMIT = 5;
    private static final int MAX_DOCUMENT_CACHE_SIZE = 500;

    // All dependencies injected via constructor (Lombok generates it)
    private final DocStore docStore;
    private final DocChunkStore docChunkStore;

    // LRU cache backed by synchronized LinkedHashMap with removeEldestEntry override
    private final Map<String, ParsedDocument> documentCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ParsedDocument> eldest) {
                    return size() > MAX_DOCUMENT_CACHE_SIZE;
                }
            });

    // @ConfigProperty can be mixed with @RequiredArgsConstructor; only final fields are injected
    @ConfigProperty(name = "app.document-cache.enabled", defaultValue = "true")
    boolean documentCacheEnabled;

    /**
     * Private record as internal value carrier — not exposed in the public API.
     * Records auto-generate constructor, accessors (no get prefix), equals, hashCode, toString.
     */
    private record ParsedDocument(
            String title,
            String description,
            String path,
            String subject,
            String extension,
            List<SectionInfo> sections,
            List<CodeBlockInfo> codeBlocks
    ) {}

    /**
     * Retrieves a document by path with full structured content.
     */
    public DocumentResponse getDocumentByPath(String version, String path) {
        ParsedDocument parsed = getOrParseDocument(version, path);
        if (parsed == null) {
            return null;
        }
        return new DocumentResponse(
                parsed.title(), parsed.description(), parsed.path(),
                parsed.subject(), parsed.extension(),
                parsed.sections(), parsed.codeBlocks(),
                List.of(), null);
    }

    /**
     * Invalidates the in-memory document parse cache for a specific version.
     */
    public void invalidateDocumentCache(String version) {
        String prefix = version + "::";
        documentCache.keySet().removeIf(key -> key.startsWith(prefix));
        log.info("Document parse cache invalidated for version {}", version);  // SLF4J via @Slf4j
    }

    private ParsedDocument getOrParseDocument(String version, String path) {
        String cacheKey = version + "::" + path;

        if (documentCacheEnabled) {
            ParsedDocument cached = documentCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        // Optional<T> as return type — never null from public API
        Optional<String> contentOpt = docStore.read(version, path);
        if (contentOpt.isEmpty()) {
            return null;
        }

        // ... parse content into sections/codeBlocks ...

        ParsedDocument parsed = new ParsedDocument(/* ... */);
        if (documentCacheEnabled) {
            documentCache.put(cacheKey, parsed);
        }
        return parsed;
    }
}
```
