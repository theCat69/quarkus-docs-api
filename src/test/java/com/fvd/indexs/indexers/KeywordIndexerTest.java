package com.fvd.indexs.indexers;

import com.fvd.asciidocs.parser.AsciidocParser;
import com.fvd.cache.services.CacheService;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import com.fvd.search.TestSearchConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordIndexerTest {

    @TempDir
    Path tempDir;

    KeywordIndexer indexer;
    DocStore docStore;
    KeywordIndexStore keywordIndexStore;

    @BeforeEach
    void setUp() {
        CacheService cacheService = new CacheService(tempDir.toString());
        docStore = new DocStore(cacheService);
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db"));
        SqliteSchemaInitializer initializer = new SqliteSchemaInitializer(ds);
        initializer.initSchema();
        keywordIndexStore = new KeywordIndexStore(ds);
        DocParser parser = new AsciidocParser(new TestSearchConfig());
        indexer = new KeywordIndexer(docStore, keywordIndexStore, parser, new TestSearchConfig());
    }

    @Test
    void buildIndexForSingleFile() {
        String doc = """
                = Security Overview
                
                Quarkus security provides authentication and authorization.
                
                == OIDC Configuration
                
                Configure the OIDC provider for your application.
                """;
        docStore.write("3.27", "security-overview.adoc", doc);

        KeywordIndex index = indexer.build("3.27", List.of("security-overview.adoc"));

        assertThat(index.files).hasSize(1);
        FileKeywordEntry entry = index.files.get(0);
        assertThat(entry.path).isEqualTo("security-overview.adoc");
        assertThat(entry.keywords).isNotEmpty();

        // "security" appears in text + filename boost (+10)
        Optional<KeywordScore> securityScore = entry.keywords.stream()
                .filter(k -> k.word.equals("security")).findFirst();
        assertThat(securityScore).isPresent();
        assertThat(securityScore.get().score).isGreaterThanOrEqualTo(11); // at least 1 occurrence + 10 filename boost

        // Sections should be present
        assertThat(entry.sections).isNotEmpty();
        assertThat(entry.sections.get(0).title).isEqualTo("Security Overview");
    }

    @Test
    void buildIndexForMultipleFiles() {
        docStore.write("3.27", "security-oidc.adoc", """
                = OIDC Guide
                
                OpenID Connect configuration for Quarkus.
                """);
        docStore.write("3.27", "config.adoc", """
                = Configuration Reference
                
                All configuration properties explained.
                """);

        KeywordIndex index = indexer.build("3.27", List.of("security-oidc.adoc", "config.adoc"));

        assertThat(index.files).hasSize(2);
        assertThat(index.files.stream().map(f -> f.path))
                .containsExactlyInAnyOrder("security-oidc.adoc", "config.adoc");
    }

    @Test
    void filenameBoostAddsToScore() {
        docStore.write("3.27", "oidc-guide.adoc", """
                = OIDC Authentication
                
                Some content about oidc authentication.
                """);

        KeywordIndex index = indexer.build("3.27", List.of("oidc-guide.adoc"));
        FileKeywordEntry entry = index.files.get(0);

        // "oidc" is in filename (boost +10) and in text (at least 1 occurrence)
        Optional<KeywordScore> oidcScore = entry.keywords.stream()
                .filter(k -> k.word.equals("oidc")).findFirst();
        assertThat(oidcScore).isPresent();
        assertThat(oidcScore.get().score).isGreaterThanOrEqualTo(12); // 2 occurrences + 10 boost

        // "guide" is in filename but not in text (only boost)
        Optional<KeywordScore> guideScore = entry.keywords.stream()
                .filter(k -> k.word.equals("guide")).findFirst();
        assertThat(guideScore).isPresent();
        assertThat(guideScore.get().score).isEqualTo(10); // filename boost only
    }

    @Test
    void sectionTitleKeywordsAreBoosted() {
        docStore.write("3.27", "test.adoc", """
                = Title
                
                == Security Configuration
                
                This section explains security configuration details.
                """);

        KeywordIndex index = indexer.build("3.27", List.of("test.adoc"));
        FileKeywordEntry entry = index.files.get(0);

        // Find the "Security Configuration" section
        Optional<SectionKeywordEntry> section = entry.sections.stream()
                .filter(s -> s.title.equals("Security Configuration")).findFirst();
        assertThat(section).isPresent();

        // "security" appears in title (boosted) and in body text
        Optional<KeywordScore> securityScore = section.get().keywords.stream()
                .filter(k -> k.word.equals("security")).findFirst();
        assertThat(securityScore).isPresent();
        // Title boost should make it higher than just the body count
        assertThat(securityScore.get().score).isGreaterThan(1);
    }

    @Test
    void buildPersistsToKeywordIndexStore() {
        docStore.write("3.27", "test.adoc", "= Simple Doc\n\nSome content.");

        indexer.build("3.27", List.of("test.adoc"));

        Optional<KeywordIndex> stored = keywordIndexStore.read("3.27");
        assertThat(stored).isPresent();
        assertThat(stored.get().files).isNotEmpty();
        assertThat(stored.get().files.get(0).path).isEqualTo("test.adoc");
    }

    @Test
    void buildReturnsEmptyIndexForNoFiles() {
        KeywordIndex index = indexer.build("3.27", List.of());

        assertThat(index.files).isEmpty();
    }

    @Test
    void buildSkipsFilesNotInCache() {
        // "missing.adoc" is not written to docStore
        KeywordIndex index = indexer.build("3.27", List.of("missing.adoc"));

        assertThat(index.files).isEmpty();
    }

    @Test
    void codeBlocksAreExcludedFromKeywords() {
        docStore.write("3.27", "test.adoc", """
                = Guide
                
                Real content here.
                
                [source,java]
                ----
                public class InternalService {
                    private String secretCode;
                }
                ----
                
                More real content.
                """);

        KeywordIndex index = indexer.build("3.27", List.of("test.adoc"));
        FileKeywordEntry entry = index.files.get(0);

        // "real" and "content" should be present
        assertThat(entry.keywords.stream().map(k -> k.word)).contains("real", "content");
        // Code block keywords should not be present
        assertThat(entry.keywords.stream().map(k -> k.word))
                .doesNotContain("class", "internalservice", "secretcode", "private");
    }
}
