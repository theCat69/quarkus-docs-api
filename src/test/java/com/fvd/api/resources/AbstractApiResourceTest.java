package com.fvd.api.resources;

import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import com.fvd.api.services.CatalogService;
import com.fvd.cache.services.CacheService;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.CodeSampleEntry;
import com.fvd.indexs.indexers.CodeSampleIndex;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.indexs.model.DocChunk;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import com.fvd.indexs.stores.DocChunkStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.search.services.SearchService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

/**
 * Shared setup and seed helpers for API resource integration tests.
 * Subclasses must be annotated with {@code @QuarkusTest}.
 */
abstract class AbstractApiResourceTest {

    @Inject
    KeywordIndexStore keywordIndexStore;

    @Inject
    CodeSampleIndexStore codeSampleIndexStore;

    @Inject
    DocChunkStore docChunkStore;

    @Inject
    DocStore docStore;

    @Inject
    DataSource dataSource;

    @Inject
    CacheService cacheService;

    @Inject
    SearchService searchService;

    @Inject
    CatalogService catalogService;

    @BeforeEach
    void cleanup() throws SQLException {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE files, file_keywords, sections, section_keywords, "
                + "code_samples, code_sample_keywords, github_index, document_metadata, doc_chunks CASCADE");
        }
        cacheService.deleteCache();
        searchService.invalidateCache("3.27");
        searchService.invalidateCache("main");
        catalogService.invalidateCache("3.27");
        catalogService.invalidateCache("main");
    }

    protected void seedDocChunks(String version, List<DocChunk> chunks) {
        docChunkStore.insertBatch(version, chunks);
    }

    protected void seedKeywordIndexMultiple() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc",
                        List.of(new KeywordScore("security", 15), new KeywordScore("quarkus", 8)),
                        List.of()),
                new FileKeywordEntry("config.adoc",
                        List.of(new KeywordScore("config", 10), new KeywordScore("quarkus", 5)),
                        List.of())
        ));
        keywordIndexStore.write("3.27", index);
    }

    protected void seedKeywordIndexWithExtensions() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc",
                        List.of(new KeywordScore("security", 15)),
                        List.of(),
                        "quarkus-core"),
                new FileKeywordEntry("config.adoc",
                        List.of(new KeywordScore("security", 10)),
                        List.of(),
                        "quarkus-openapi-generator")
        ));
        keywordIndexStore.write("3.27", index);
    }

    protected void seedDocFilesMultiple() {
        docStore.write("3.27", "security.adoc", "= Security\nContent about security and quarkus.");
        docStore.write("3.27", "config.adoc", "= Config\nContent about config and quarkus.");
    }

    protected void seedCodeSampleIndex() {
        CodeSampleIndex index = new CodeSampleIndex(List.of(
                new CodeSampleEntry("security.adoc", "Authentication", "java",
                        "import io.quarkus.security.identity.SecurityIdentity;",
                        5, 10,
                        List.of(new KeywordScore("security", 15), new KeywordScore("identity", 8))),
                new CodeSampleEntry("config.adoc", "Authorization", "java",
                        "@RolesAllowed(\"admin\")",
                        20, 25,
                        List.of(new KeywordScore("security", 10), new KeywordScore("roles", 5)))
        ));
        codeSampleIndexStore.write("3.27", index);
    }
}
