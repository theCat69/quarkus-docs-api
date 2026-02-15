package com.fvd.quarkiverse.services;

import com.fvd.cache.services.CacheService;
import com.fvd.common.TestZipHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuarkiverseZipExtractorTest {

    @TempDir
    Path tempDir;

    private CacheService cacheService;
    private QuarkiverseZipExtractor extractor;

    @BeforeEach
    void setUp() {
        cacheService = mock(CacheService.class);
        when(cacheService.versionDir("main")).thenReturn(tempDir.resolve("main"));
        extractor = new QuarkiverseZipExtractor();
    }

    @Test
    void extractsAdocFilesFromRootModulePages() throws Exception {
        InputStream zip = TestZipHelper.createZipAsStream(
                "quarkus-openapi-generator-main/docs/modules/ROOT/pages/index.adoc", "= Index",
                "quarkus-openapi-generator-main/docs/modules/ROOT/pages/usage.adoc", "= Usage"
        );

        List<String> result = extractor.extractDocs(zip, "quarkus-openapi-generator", "docs", cacheService);

        assertThat(result).containsExactlyInAnyOrder(
                "quarkiverse/quarkus-openapi-generator/index.adoc",
                "quarkiverse/quarkus-openapi-generator/usage.adoc"
        );

        // Verify files were written
        Path docsDir = tempDir.resolve("main/docs/quarkiverse/quarkus-openapi-generator");
        assertThat(Files.readString(docsDir.resolve("index.adoc"))).isEqualTo("= Index");
        assertThat(Files.readString(docsDir.resolve("usage.adoc"))).isEqualTo("= Usage");
    }

    @Test
    void ignoresNonAdocFiles() throws Exception {
        InputStream zip = TestZipHelper.createZipAsStream(
                "repo-main/docs/modules/ROOT/pages/index.adoc", "= Index",
                "repo-main/docs/modules/ROOT/pages/image.png", "binary-data",
                "repo-main/docs/modules/ROOT/pages/readme.md", "# Readme"
        );

        List<String> result = extractor.extractDocs(zip, "my-ext", "docs", cacheService);

        assertThat(result).containsExactly("quarkiverse/my-ext/index.adoc");
    }

    @Test
    void ignoresNonRootModules() throws Exception {
        InputStream zip = TestZipHelper.createZipAsStream(
                "repo-main/docs/modules/ROOT/pages/index.adoc", "= Index",
                "repo-main/docs/modules/reference/pages/ref.adoc", "= Reference"
        );

        List<String> result = extractor.extractDocs(zip, "my-ext", "docs", cacheService);

        assertThat(result).containsExactly("quarkiverse/my-ext/index.adoc");
    }

    @Test
    void handlesEmptyStartPath() throws Exception {
        InputStream zip = TestZipHelper.createZipAsStream(
                "repo-main/modules/ROOT/pages/index.adoc", "= Index"
        );

        List<String> result = extractor.extractDocs(zip, "my-ext", "", cacheService);

        assertThat(result).containsExactly("quarkiverse/my-ext/index.adoc");
    }

    @Test
    void handlesNestedPagesDirectory() throws Exception {
        InputStream zip = TestZipHelper.createZipAsStream(
                "repo-main/docs/modules/ROOT/pages/sub/nested.adoc", "= Nested"
        );

        List<String> result = extractor.extractDocs(zip, "my-ext", "docs", cacheService);

        assertThat(result).containsExactly("quarkiverse/my-ext/sub/nested.adoc");
    }

    @Test
    void returnsEmptyForZipWithNoMatchingEntries() throws Exception {
        InputStream zip = TestZipHelper.createZipAsStream(
                "repo-main/src/main/java/Foo.java", "class Foo {}"
        );

        List<String> result = extractor.extractDocs(zip, "my-ext", "docs", cacheService);

        assertThat(result).isEmpty();
    }

    @Test
    void extractsTitleFromAntoraYml() throws Exception {
        String antoraYml = "name: quarkus-openapi-generator\ntitle: Quarkus OpenAPI Generator\nversion: ~\n";
        InputStream zip = TestZipHelper.createZipAsStream(
                "repo-main/docs/antora.yml", antoraYml,
                "repo-main/docs/modules/ROOT/pages/index.adoc", "= Index"
        );

        extractor.extractDocs(zip, "quarkus-openapi-generator", "docs", cacheService);

        Path titleFile = tempDir.resolve("main/docs/quarkiverse/quarkus-openapi-generator/.extension-title");
        assertThat(titleFile).exists();
        assertThat(Files.readString(titleFile)).isEqualTo("Quarkus OpenAPI Generator");
    }

    @Test
    void noTitleFileWhenAntoraYmlMissing() throws Exception {
        InputStream zip = TestZipHelper.createZipAsStream(
                "repo-main/docs/modules/ROOT/pages/index.adoc", "= Index"
        );

        extractor.extractDocs(zip, "my-ext", "docs", cacheService);

        Path titleFile = tempDir.resolve("main/docs/quarkiverse/my-ext/.extension-title");
        assertThat(titleFile).doesNotExist();
    }

    @Test
    void noTitleFileWhenAntoraYmlHasNoTitle() throws Exception {
        String antoraYml = "name: my-ext\nversion: ~\n";
        InputStream zip = TestZipHelper.createZipAsStream(
                "repo-main/docs/antora.yml", antoraYml,
                "repo-main/docs/modules/ROOT/pages/index.adoc", "= Index"
        );

        extractor.extractDocs(zip, "my-ext", "docs", cacheService);

        Path titleFile = tempDir.resolve("main/docs/quarkiverse/my-ext/.extension-title");
        assertThat(titleFile).doesNotExist();
    }

    @Test
    void malformedAntoraYmlDoesNotBreakExtraction() throws Exception {
        InputStream zip = TestZipHelper.createZipAsStream(
                "repo-main/docs/antora.yml", "{{invalid yaml!@#",
                "repo-main/docs/modules/ROOT/pages/index.adoc", "= Index"
        );

        List<String> result = extractor.extractDocs(zip, "my-ext", "docs", cacheService);

        assertThat(result).containsExactly("quarkiverse/my-ext/index.adoc");
        Path titleFile = tempDir.resolve("main/docs/quarkiverse/my-ext/.extension-title");
        assertThat(titleFile).doesNotExist();
    }

}
