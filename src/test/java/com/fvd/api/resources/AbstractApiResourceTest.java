package com.fvd.api.resources;

import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;

import com.fvd.api.services.CatalogService;
import com.fvd.cache.services.CacheService;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.model.DocChunk;
import com.fvd.indexs.stores.DocChunkStore;

/**
 * Shared setup and seed helpers for API resource integration tests.
 * Subclasses must be annotated with {@code @QuarkusTest}.
 */
abstract class AbstractApiResourceTest {

    @Inject
    DocChunkStore docChunkStore;

    @Inject
    DocStore docStore;

    @Inject
    DataSource dataSource;

    @Inject
    CacheService cacheService;

    @Inject
    CatalogService catalogService;

    @BeforeEach
    void cleanup() throws SQLException {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE github_index, doc_chunks CASCADE");
        }
        cacheService.deleteCache();
        catalogService.invalidateCache("3.27");
        catalogService.invalidateCache("main");
    }

    protected void seedDocChunks(String version, List<DocChunk> chunks) {
        docChunkStore.insertBatch(version, chunks);
    }

    protected void seedDocChunksMultiple() {
        seedDocChunks("3.27", List.of(
                new DocChunk("chunk-security-1", "3.27", "security", "Security Guide", "Overview",
                        "https://quarkus.io/guides/security",
                        List.of("security"), List.of("quarkus-core"),
                        "Overview of security features in Quarkus",
                        "This guide covers security basics and quarkus security features for authentication and authorization."),
                new DocChunk("chunk-config-1", "3.27", "config", "Configuration Guide", "Overview",
                        "https://quarkus.io/guides/config",
                        List.of("config"), List.of("quarkus-core"),
                        "Overview of Quarkus configuration",
                        "This guide covers config basics and quarkus configuration for application properties and settings.")
        ));
    }

    protected void seedDocChunksWithExtensions() {
        seedDocChunks("3.27", List.of(
                new DocChunk("chunk-ext-security-1", "3.27", "security", "Security Guide", "Overview",
                        "https://quarkus.io/guides/security",
                        List.of("security"), List.of("quarkus-core"),
                        "Overview of security features",
                        "This guide covers security basics and authentication in quarkus core."),
                new DocChunk("chunk-ext-config-1", "3.27", "config", "Configuration Guide", "Overview",
                        "https://quarkus.io/guides/config",
                        List.of("security"), List.of("quarkus-openapi-generator"),
                        "Overview of security configuration",
                        "This guide covers security configuration for the openapi generator extension.")
        ));
    }

    protected void seedDocFilesMultiple() {
        docStore.write("3.27", "security.adoc", "= Security\nContent about security and quarkus.");
        docStore.write("3.27", "config.adoc", "= Config\nContent about config and quarkus.");
    }
}
