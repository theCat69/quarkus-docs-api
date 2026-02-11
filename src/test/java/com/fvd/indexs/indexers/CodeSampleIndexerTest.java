package com.fvd.indexs.indexers;

import com.fvd.asciidocs.parser.AsciidocParser;
import com.fvd.cache.services.CacheService;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import com.fvd.search.TestSearchConfig;
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
        DocParser parser = new AsciidocParser(new TestSearchConfig());
        indexer = new CodeSampleIndexer(docStore, store, parser, new TestSearchConfig());
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
        // Section keywords "oidc", "authentication" (stemmed to "authentic"), "setup" should be present
        assertThat(keywordMap).containsKey("oidc");
        assertThat(keywordMap).containsKey("authentic");
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

        // io.restassured is NOT a known framework package — no boost applied
        assertThat(keywords).isEmpty();
    }

    @Test
    void applyImportBoostIgnoresNonFrameworkImports() {
        Map<String, Integer> keywords = new HashMap<>();
        String code = "import io.x.Foo;";

        indexer.applyImportBoost(code, keywords);

        // io.x does not start with any known package — no boost applied
        assertThat(keywords).isEmpty();
    }

    @Test
    void applyFilenameBoostAddsScoreForFilenameTokens() {
        Map<String, Integer> keywords = new HashMap<>();

        indexer.applyFilenameBoost("security-oidc.adoc", keywords);

        assertThat(keywords.get("secur")).isEqualTo(10);
        assertThat(keywords.get("oidc")).isEqualTo(10);
    }

    @Test
    void applyFilenameBoostHandlesPathWithDirectories() {
        Map<String, Integer> keywords = new HashMap<>();

        indexer.applyFilenameBoost("guides/security-oidc.adoc", keywords);

        assertThat(keywords.get("secur")).isEqualTo(10);
        assertThat(keywords.get("oidc")).isEqualTo(10);
        assertThat(keywords).doesNotContainKey("guid");
    }

    @Test
    void applyFilenameBoostMergesWithExistingKeywords() {
        Map<String, Integer> keywords = new HashMap<>();
        keywords.put("secur", 3);

        indexer.applyFilenameBoost("security-oidc.adoc", keywords);

        assertThat(keywords.get("secur")).isEqualTo(13);
        assertThat(keywords.get("oidc")).isEqualTo(10);
    }

    @Test
    void applySectionTitleBoostAddsScoreForTitleTokens() {
        Map<String, Integer> keywords = new HashMap<>();

        indexer.applySectionTitleBoost("OIDC Authentication", keywords);

        assertThat(keywords.get("oidc")).isEqualTo(5);
        assertThat(keywords.get("authentic")).isEqualTo(5);
    }

    @Test
    void applySectionTitleBoostMergesWithExistingKeywords() {
        Map<String, Integer> keywords = new HashMap<>();
        keywords.put("oidc", 2);

        indexer.applySectionTitleBoost("OIDC Configuration", keywords);

        assertThat(keywords.get("oidc")).isEqualTo(7);
        assertThat(keywords.get("configur")).isEqualTo(5);
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
        // "security" stems to "secur"; should have filename boost (10) + possibly section/content score
        assertThat(keywordMap.get("secur")).isGreaterThanOrEqualTo(10);
        // "oidc" should have filename boost (10) + section title boost (5) + section keywords
        assertThat(keywordMap.get("oidc")).isGreaterThanOrEqualTo(15);
        // "configuration" stems to "configur"; should have section title boost (5) + section keywords
        assertThat(keywordMap.get("configur")).isGreaterThanOrEqualTo(5);
        // "inject" should still have import boost
        assertThat(keywordMap.get("inject")).isGreaterThanOrEqualTo(5);
    }

    @Test
    void applyImportBoostHandlesStaticImportsFromKnownPackages() {
        Map<String, Integer> keywords = new HashMap<>();
        String code = "import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;";

        indexer.applyImportBoost(code, keywords);

        assertThat(keywords).isNotEmpty();
        assertThat(keywords.get("jakarta")).isEqualTo(5);
    }

    @Test
    void applyImportBoostOnlyBoostsKnownPackages() {
        Map<String, Integer> keywords = new HashMap<>();
        String code = "import jakarta.ws.rs.GET;\nimport java.util.List;\nimport org.eclipse.microprofile.config.inject.ConfigProperty;";

        indexer.applyImportBoost(code, keywords);

        // jakarta and org.eclipse.microprofile are known; java.util is NOT
        assertThat(keywords.get("jakarta")).isNotNull();
        assertThat(keywords).doesNotContainKey("java");
        assertThat(keywords).doesNotContainKey("util");
        assertThat(keywords).doesNotContainKey("list");
        // org.eclipse.microprofile keywords should be boosted
        assertThat(keywords.get("eclipse")).isNotNull(); // "eclipse" stemmed
    }

    @Test
    void applyAnnotationBoostResolvesKnownPackageAnnotation() {
        Map<String, Integer> keywords = new HashMap<>();
        String code = "import jakarta.enterprise.context.ApplicationScoped;\n\n@ApplicationScoped\npublic class Foo {}";

        indexer.applyAnnotationBoost(code, keywords);

        // "applicationscoped" → Stemmer.stem("applicationscoped") with boost 10
        assertThat(keywords).isNotEmpty();
        String stemmed = com.fvd.common.Stemmer.stem("applicationscoped");
        assertThat(keywords.get(stemmed)).isEqualTo(10);
    }

    @Test
    void applyAnnotationBoostResolvesPathAnnotation() {
        Map<String, Integer> keywords = new HashMap<>();
        String code = "import jakarta.ws.rs.Path;\n\n@Path(\"/hello\")\npublic class Foo {}";

        indexer.applyAnnotationBoost(code, keywords);

        assertThat(keywords).isNotEmpty();
        assertThat(keywords.get("path")).isEqualTo(10);
    }

    @Test
    void applyAnnotationBoostIgnoresAnnotationWithoutImport() {
        Map<String, Integer> keywords = new HashMap<>();
        String code = "@Override\npublic String toString() { return \"\"; }";

        indexer.applyAnnotationBoost(code, keywords);

        assertThat(keywords).isEmpty();
    }

    @Test
    void applyAnnotationBoostIgnoresNonKnownPackageAnnotation() {
        Map<String, Integer> keywords = new HashMap<>();
        String code = "import com.example.MyCustomAnnotation;\n\n@MyCustomAnnotation\npublic class Foo {}";

        indexer.applyAnnotationBoost(code, keywords);

        assertThat(keywords).isEmpty();
    }

    @Test
    void applyAnnotationBoostHandlesMultipleAnnotations() {
        Map<String, Integer> keywords = new HashMap<>();
        String code = "import jakarta.ws.rs.Path;\nimport jakarta.ws.rs.GET;\n\n@Path(\"/hello\")\npublic class Foo {\n    @GET\n    public String get() {}\n}";

        indexer.applyAnnotationBoost(code, keywords);

        assertThat(keywords.get("path")).isEqualTo(10);
        assertThat(keywords.get("get")).isEqualTo(10);
    }

    @Test
    void applyAnnotationBoostOnlyBoostsEachAnnotationOnce() {
        Map<String, Integer> keywords = new HashMap<>();
        String code = "import jakarta.ws.rs.GET;\n\n@GET\npublic String foo() {}\n@GET\npublic String bar() {}";

        indexer.applyAnnotationBoost(code, keywords);

        assertThat(keywords.get("get")).isEqualTo(10);
    }

    private Map<String, Integer> toMap(List<KeywordScore> scores) {
        Map<String, Integer> map = new HashMap<>();
        for (KeywordScore ks : scores) {
            map.put(ks.word, ks.score);
        }
        return map;
    }
}
