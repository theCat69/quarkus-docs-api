package com.fvd.indexs.indexers;

import com.fvd.asciidocs.parser.AsciidocParser;
import com.fvd.cache.services.CacheService;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CodeSampleIndexerTest {

    @TempDir
    Path tempDir;

    private CodeSampleIndexer indexer;
    private CodeSampleIndexStore store;
    private DocStore docStore;

    @BeforeEach
    void setUp() {
        CacheService cacheService = new CacheService(tempDir.toString());
        docStore = new DocStore(cacheService);

        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db"));
        SqliteSchemaInitializer schemaInitializer = new SqliteSchemaInitializer(ds);
        schemaInitializer.initSchema();

        store = new CodeSampleIndexStore(ds);
        DocParser parser = new AsciidocParser();
        indexer = new CodeSampleIndexer(docStore, store, parser);
    }

    private void writeDoc(String version, String filePath, String content) throws IOException {
        Path docFile = tempDir.resolve(version).resolve("docs").resolve(filePath);
        Files.createDirectories(docFile.getParent());
        Files.writeString(docFile, content);
    }

    @Test
    void buildExtractsCodeSamplesFromFile() throws IOException {
        String doc = """
                = Security Guide
                
                == Configuration
                
                Configure the OIDC provider.
                
                [source,java]
                ----
                import jakarta.inject.Inject;
                
                @Inject
                OidcConfig config;
                ----
                """;
        writeDoc("3.17", "security-oidc.adoc", doc);

        CodeSampleIndex index = indexer.build("3.17", List.of("security-oidc.adoc"));

        assertThat(index.samples).hasSize(1);
        CodeSampleEntry entry = index.samples.get(0);
        assertThat(entry.filePath).isEqualTo("security-oidc.adoc");
        assertThat(entry.sectionTitle).isEqualTo("Configuration");
        assertThat(entry.language).isEqualTo("java");
        assertThat(entry.content).contains("import jakarta.inject.Inject;");
    }

    @Test
    void buildBoostsImportKeywords() throws IOException {
        String doc = """
                = Guide
                
                == Endpoint
                
                [source,java]
                ----
                import jakarta.ws.rs.GET;
                import jakarta.ws.rs.Path;
                
                @Path("/hello")
                public class HelloResource {
                    @GET
                    public String hello() { return "hi"; }
                }
                ----
                """;
        writeDoc("3.17", "rest.adoc", doc);

        CodeSampleIndex index = indexer.build("3.17", List.of("rest.adoc"));

        assertThat(index.samples).hasSize(1);
        CodeSampleEntry entry = index.samples.get(0);

        // Import keywords should be boosted by +5 each
        Map<String, Integer> keywordMap = toMap(entry.keywords);
        // "jakarta" appears in 2 imports, so base tokenize count + 2*5 = 10+ boost
        assertThat(keywordMap.get("jakarta")).isGreaterThanOrEqualTo(10);
        // "get" from jakarta.ws.rs.GET import
        assertThat(keywordMap.get("get")).isGreaterThanOrEqualTo(5);
        // "path" from jakarta.ws.rs.Path import
        assertThat(keywordMap.get("path")).isGreaterThanOrEqualTo(5);
    }

    @Test
    void buildIncludesSectionKeywords() throws IOException {
        String doc = """
                = Guide
                
                == OIDC Authentication
                
                This section covers oidc authentication setup.
                
                [source,java]
                ----
                public class OidcFilter {
                }
                ----
                """;
        writeDoc("3.17", "oidc.adoc", doc);

        CodeSampleIndex index = indexer.build("3.17", List.of("oidc.adoc"));

        assertThat(index.samples).hasSize(1);
        Map<String, Integer> keywordMap = toMap(index.samples.get(0).keywords);
        // Section keywords "oidc", "authentication", "setup" should be present
        assertThat(keywordMap).containsKey("oidc");
        assertThat(keywordMap).containsKey("authentication");
    }

    @Test
    void buildHandlesMultipleCodeBlocksInOneFile() throws IOException {
        String doc = """
                = Guide
                
                == Section A
                
                [source,java]
                ----
                code block one
                ----
                
                == Section B
                
                [source,xml]
                ----
                <code>two</code>
                ----
                """;
        writeDoc("3.17", "multi.adoc", doc);

        CodeSampleIndex index = indexer.build("3.17", List.of("multi.adoc"));

        assertThat(index.samples).hasSize(2);
        assertThat(index.samples.get(0).sectionTitle).isEqualTo("Section A");
        assertThat(index.samples.get(0).language).isEqualTo("java");
        assertThat(index.samples.get(1).sectionTitle).isEqualTo("Section B");
        assertThat(index.samples.get(1).language).isEqualTo("xml");
    }

    @Test
    void buildSkipsMissingFiles() throws IOException {
        String doc = """
                = Guide
                
                [source,java]
                ----
                some code
                ----
                """;
        writeDoc("3.17", "exists.adoc", doc);

        CodeSampleIndex index = indexer.build("3.17", List.of("exists.adoc", "missing.adoc"));

        assertThat(index.samples).hasSize(1);
        assertThat(index.samples.get(0).filePath).isEqualTo("exists.adoc");
    }

    @Test
    void buildReturnsEmptyForNoCodeBlocks() throws IOException {
        String doc = """
                = Guide
                
                Just text, no code blocks.
                """;
        writeDoc("3.17", "nocode.adoc", doc);

        CodeSampleIndex index = indexer.build("3.17", List.of("nocode.adoc"));

        assertThat(index.samples).isEmpty();
    }

    @Test
    void buildReturnsEmptyForEmptyFileList() {
        CodeSampleIndex index = indexer.build("3.17", List.of());

        assertThat(index.samples).isEmpty();
    }

    @Test
    void buildPersistsToStore() throws IOException {
        String doc = """
                = Guide
                
                [source,java]
                ----
                import jakarta.inject.Inject;
                ----
                """;
        writeDoc("3.17", "file.adoc", doc);

        indexer.build("3.17", List.of("file.adoc"));

        assertThat(store.exists("3.17")).isTrue();
        var loaded = store.read("3.17");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().samples).hasSize(1);
    }

    @Test
    void applyImportBoostAddsScoreForFQCN() {
        Map<String, Integer> keywords = new HashMap<>();
        String code = "import jakarta.ws.rs.GET;\nimport jakarta.inject.Inject;";

        indexer.applyImportBoost(code, keywords);

        // "jakarta" appears in 2 imports: 2 * 5 = 10
        assertThat(keywords.get("jakarta")).isEqualTo(10);
        // "inject" appears twice in jakarta.inject.Inject (package + class): 2 * 5 = 10
        assertThat(keywords.get("inject")).isEqualTo(10);
        // "get" from jakarta.ws.rs.GET: 5
        assertThat(keywords.get("get")).isEqualTo(5);
    }

    @Test
    void applyImportBoostHandlesStaticImports() {
        Map<String, Integer> keywords = new HashMap<>();
        String code = "import static io.restassured.RestAssured.given;";

        indexer.applyImportBoost(code, keywords);

        // "restassured" appears twice (package + class): 2 * 5 = 10
        assertThat(keywords.get("restassured")).isEqualTo(10);
        assertThat(keywords.get("given")).isEqualTo(5);
    }

    @Test
    void applyImportBoostIgnoresShortTokens() {
        Map<String, Integer> keywords = new HashMap<>();
        String code = "import io.x.Foo;";

        indexer.applyImportBoost(code, keywords);

        // "io" and "x" are too short (< 3 chars), should not be added
        assertThat(keywords).doesNotContainKey("io");
        assertThat(keywords).doesNotContainKey("x");
        // "foo" should be added
        assertThat(keywords.get("foo")).isEqualTo(5);
    }

    @Test
    void applyFilenameBoostAddsScoreForFilenameTokens() {
        Map<String, Integer> keywords = new HashMap<>();

        indexer.applyFilenameBoost("security-oidc.adoc", keywords);

        assertThat(keywords.get("security")).isEqualTo(10);
        assertThat(keywords.get("oidc")).isEqualTo(10);
    }

    @Test
    void applyFilenameBoostHandlesPathWithDirectories() {
        Map<String, Integer> keywords = new HashMap<>();

        indexer.applyFilenameBoost("guides/security-oidc.adoc", keywords);

        assertThat(keywords.get("security")).isEqualTo(10);
        assertThat(keywords.get("oidc")).isEqualTo(10);
        assertThat(keywords).doesNotContainKey("guides");
    }

    @Test
    void applyFilenameBoostMergesWithExistingKeywords() {
        Map<String, Integer> keywords = new HashMap<>();
        keywords.put("security", 3);

        indexer.applyFilenameBoost("security-oidc.adoc", keywords);

        assertThat(keywords.get("security")).isEqualTo(13);
        assertThat(keywords.get("oidc")).isEqualTo(10);
    }

    @Test
    void applySectionTitleBoostAddsScoreForTitleTokens() {
        Map<String, Integer> keywords = new HashMap<>();

        indexer.applySectionTitleBoost("OIDC Authentication", keywords);

        assertThat(keywords.get("oidc")).isEqualTo(5);
        assertThat(keywords.get("authentication")).isEqualTo(5);
    }

    @Test
    void applySectionTitleBoostMergesWithExistingKeywords() {
        Map<String, Integer> keywords = new HashMap<>();
        keywords.put("oidc", 2);

        indexer.applySectionTitleBoost("OIDC Configuration", keywords);

        assertThat(keywords.get("oidc")).isEqualTo(7);
        assertThat(keywords.get("configuration")).isEqualTo(5);
    }

    @Test
    void applySectionTitleBoostHandsNullTitle() {
        Map<String, Integer> keywords = new HashMap<>();
        keywords.put("existing", 1);

        indexer.applySectionTitleBoost(null, keywords);

        assertThat(keywords).hasSize(1);
        assertThat(keywords.get("existing")).isEqualTo(1);
    }

    @Test
    void applySectionTitleBoostHandlesBlankTitle() {
        Map<String, Integer> keywords = new HashMap<>();
        keywords.put("existing", 1);

        indexer.applySectionTitleBoost("   ", keywords);

        assertThat(keywords).hasSize(1);
        assertThat(keywords.get("existing")).isEqualTo(1);
    }

    @Test
    void buildAppliesAllBoosts() throws IOException {
        String doc = """
                = Security Guide
                
                == OIDC Configuration
                
                Configure the OIDC provider.
                
                [source,java]
                ----
                import jakarta.inject.Inject;
                
                @Inject
                OidcConfig config;
                ----
                """;
        writeDoc("3.17", "security-oidc.adoc", doc);

        CodeSampleIndex index = indexer.build("3.17", List.of("security-oidc.adoc"));

        assertThat(index.samples).hasSize(1);
        Map<String, Integer> keywordMap = toMap(index.samples.get(0).keywords);
        // "security" should have filename boost (10) + possibly section/content score
        assertThat(keywordMap.get("security")).isGreaterThanOrEqualTo(10);
        // "oidc" should have filename boost (10) + section title boost (5) + section keywords
        assertThat(keywordMap.get("oidc")).isGreaterThanOrEqualTo(15);
        // "configuration" should have section title boost (5) + section keywords
        assertThat(keywordMap.get("configuration")).isGreaterThanOrEqualTo(5);
        // "inject" should still have import boost
        assertThat(keywordMap.get("inject")).isGreaterThanOrEqualTo(5);
    }

    private Map<String, Integer> toMap(List<KeywordScore> scores) {
        Map<String, Integer> map = new HashMap<>();
        for (KeywordScore ks : scores) {
            map.put(ks.word, ks.score);
        }
        return map;
    }
}
