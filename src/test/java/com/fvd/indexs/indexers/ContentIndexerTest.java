package com.fvd.indexs.indexers;

import com.fvd.cache.services.CacheService;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.stores.ContentIndexStore;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import com.fvd.search.TestSearchConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentIndexerTest {

    @TempDir
    Path tempDir;

    private ContentIndexer indexer;
    private ContentIndexStore store;
    private DocStore docStore;

    @BeforeEach
    void setUp() {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db"));
        SqliteSchemaInitializer initializer = new SqliteSchemaInitializer(ds);
        initializer.initSchema();
        store = new ContentIndexStore(ds);
        CacheService cacheService = new CacheService(tempDir.toString());
        docStore = new DocStore(cacheService);
        var config = new TestSearchConfig();
        indexer = new ContentIndexer(docStore, store, config);
    }

    @Test
    void buildIndexesWordPositions() {
        docStore.write("3.27", "test.adoc", "Quarkus security guide for REST endpoints");

        ContentIndex index = indexer.build("3.27", List.of("test.adoc"));

        assertThat(index.wordOccurrences).containsKey("quarkus");
        assertThat(index.wordOccurrences).containsKey("security");
        assertThat(index.wordOccurrences).containsKey("guide");
        assertThat(index.wordOccurrences).containsKey("rest");
        assertThat(index.wordOccurrences).containsKey("endpoints");
    }

    @Test
    void buildFiltersStopWords() {
        docStore.write("3.27", "test.adoc", "the security and authentication for the application");

        ContentIndex index = indexer.build("3.27", List.of("test.adoc"));

        // "the", "and", "for" are stop words
        assertThat(index.wordOccurrences).doesNotContainKey("the");
        assertThat(index.wordOccurrences).doesNotContainKey("and");
        assertThat(index.wordOccurrences).doesNotContainKey("for");
        // "security", "authentication", "application" should be indexed
        assertThat(index.wordOccurrences).containsKey("security");
        assertThat(index.wordOccurrences).containsKey("authentication");
        assertThat(index.wordOccurrences).containsKey("application");
    }

    @Test
    void buildFiltersShortTokens() {
        docStore.write("3.27", "test.adoc", "a is on it at security");

        ContentIndex index = indexer.build("3.27", List.of("test.adoc"));

        // Tokens shorter than minTokenLength (3) should be excluded
        assertThat(index.wordOccurrences).doesNotContainKey("is");
        assertThat(index.wordOccurrences).doesNotContainKey("it");
        assertThat(index.wordOccurrences).containsKey("security");
    }

    @Test
    void buildRecordsCorrectLineNumbers() {
        String content = "line one has security\nline two has nothing\nline three has quarkus";
        docStore.write("3.27", "test.adoc", content);

        ContentIndex index = indexer.build("3.27", List.of("test.adoc"));

        List<ContentOccurrence> securityOccs = index.wordOccurrences.get("security");
        assertThat(securityOccs).hasSize(1);
        assertThat(securityOccs.get(0).lineNumber).isEqualTo(1);

        List<ContentOccurrence> quarkusOccs = index.wordOccurrences.get("quarkus");
        assertThat(quarkusOccs).hasSize(1);
        assertThat(quarkusOccs.get(0).lineNumber).isEqualTo(3);
    }

    @Test
    void buildRecordsCorrectCharOffsets() {
        String content = "security guide";
        docStore.write("3.27", "test.adoc", content);

        ContentIndex index = indexer.build("3.27", List.of("test.adoc"));

        List<ContentOccurrence> securityOccs = index.wordOccurrences.get("security");
        assertThat(securityOccs).hasSize(1);
        assertThat(securityOccs.get(0).charOffset).isEqualTo(0);

        List<ContentOccurrence> guideOccs = index.wordOccurrences.get("guide");
        assertThat(guideOccs).hasSize(1);
        assertThat(guideOccs.get(0).charOffset).isEqualTo(9);
    }

    @Test
    void buildIndexesMultipleFiles() {
        docStore.write("3.27", "security.adoc", "security authentication");
        docStore.write("3.27", "config.adoc", "configuration settings");

        ContentIndex index = indexer.build("3.27", List.of("security.adoc", "config.adoc"));

        assertThat(index.wordOccurrences).containsKey("security");
        assertThat(index.wordOccurrences).containsKey("configuration");

        // security only in first file
        List<ContentOccurrence> secOccs = index.wordOccurrences.get("security");
        assertThat(secOccs).hasSize(1);
        assertThat(secOccs.get(0).filePath).isEqualTo("security.adoc");

        // configuration only in second file
        List<ContentOccurrence> confOccs = index.wordOccurrences.get("configuration");
        assertThat(confOccs).hasSize(1);
        assertThat(confOccs.get(0).filePath).isEqualTo("config.adoc");
    }

    @Test
    void buildPersistsToStore() {
        docStore.write("3.27", "test.adoc", "quarkus security guide");

        indexer.build("3.27", List.of("test.adoc"));

        assertThat(store.exists("3.27")).isTrue();
        var loaded = store.read("3.27");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().wordOccurrences).containsKey("quarkus");
    }

    @Test
    void buildIsCaseInsensitive() {
        docStore.write("3.27", "test.adoc", "Security SECURITY Security");

        ContentIndex index = indexer.build("3.27", List.of("test.adoc"));

        assertThat(index.wordOccurrences).containsKey("security");
        assertThat(index.wordOccurrences.get("security")).hasSize(3);
    }

    @Test
    void buildHandlesMissingFiles() {
        ContentIndex index = indexer.build("3.27", List.of("missing.adoc"));

        assertThat(index.wordOccurrences).isEmpty();
    }

    @Test
    void buildTokenizesIncludingCodeBlocks() {
        String content = """
                = Title
                Some text.
                
                [source,java]
                ----
                import jakarta.inject.Inject;
                ----
                """;
        docStore.write("3.27", "test.adoc", content);

        ContentIndex index = indexer.build("3.27", List.of("test.adoc"));

        // Code block content should be indexed
        assertThat(index.wordOccurrences).containsKey("inject");
        assertThat(index.wordOccurrences).containsKey("jakarta");
    }

    @Test
    void buildRecordsMultipleOccurrencesInSameFile() {
        docStore.write("3.27", "test.adoc", "security is about security and more security");

        ContentIndex index = indexer.build("3.27", List.of("test.adoc"));

        List<ContentOccurrence> occs = index.wordOccurrences.get("security");
        assertThat(occs).hasSize(3);
        // All should be in the same file
        assertThat(occs).allMatch(o -> o.filePath.equals("test.adoc"));
    }

    @Test
    void buildWordBoundaryDoesNotMatchSubstrings() {
        // "rest" should be its own token, not match inside "forest" or "interest"
        docStore.write("3.27", "test.adoc", "rest forest interest");

        ContentIndex index = indexer.build("3.27", List.of("test.adoc"));

        assertThat(index.wordOccurrences.get("rest")).hasSize(1);
        assertThat(index.wordOccurrences.get("forest")).hasSize(1);
        assertThat(index.wordOccurrences.get("interest")).hasSize(1);
    }
}
