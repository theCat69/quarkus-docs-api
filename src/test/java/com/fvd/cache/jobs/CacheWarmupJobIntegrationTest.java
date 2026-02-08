package com.fvd.cache.jobs;

import com.fvd.docs.stores.DocStore;
import com.fvd.github.services.ZipDownloadService;
import com.fvd.indexs.indexers.CodeSampleIndex;
import com.fvd.indexs.indexers.CodeSampleIndexer;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.indexs.services.IndexService;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class CacheWarmupJobIntegrationTest {

    @Inject
    ZipDownloadService zipDownloadService;

    @Inject
    IndexService indexService;

    @Inject
    KeywordIndexer keywordIndexer;

    @Inject
    CodeSampleIndexer codeSampleIndexer;

    @Inject
    DocStore docStore;

    @Inject
    KeywordIndexStore keywordIndexStore;

    @Inject
    CodeSampleIndexStore codeSampleIndexStore;

    @Inject
    SqliteSchemaInitializer schemaInitializer;

    @BeforeEach
    void cleanTestCache() throws IOException {
        var cachePath = Path.of("build/test-cache").toFile();
        if (cachePath.exists()) {
            FileUtils.cleanDirectory(cachePath);
        }
        schemaInitializer.initSchema();
    }

    @Test
    void warmupExtractsDocsFromZipAndBuildsIndexes() {
        // Step 1: Download zip and extract docs (WireMock serves the zip)
        List<String> extractedFiles = zipDownloadService.streamAndExtract("3.27");

        // Verify files were extracted
        assertThat(extractedFiles).containsExactlyInAnyOrder(
                "security-overview.adoc",
                "config.adoc"
        );

        // Step 2: Verify docs are readable from the cache
        Optional<String> securityDoc = docStore.read("3.27", "security-overview.adoc");
        assertThat(securityDoc).isPresent();
        assertThat(securityDoc.get()).contains("Quarkus Security overview");
        assertThat(securityDoc.get()).contains("SecurityIdentity");

        Optional<String> configDoc = docStore.read("3.27", "config.adoc");
        assertThat(configDoc).isPresent();
        assertThat(configDoc.get()).contains("Configuration Guide");

        // Step 3: Fetch the index from GitHub API (WireMock serves the index)
        var index = indexService.getOrFetchIndex("3.27");
        assertThat(index).isNotEmpty();
        assertThat(index).extracting("name")
                .contains("security-overview.adoc", "config.adoc");

        // Step 4: Build keyword index from extracted files
        KeywordIndex keywordIndex = keywordIndexer.build("3.27", extractedFiles);
        assertThat(keywordIndex.files).isNotEmpty();

        // Verify keyword index was persisted
        assertThat(keywordIndexStore.exists("3.27")).isTrue();

        // Verify keywords were extracted from doc content
        Optional<KeywordIndex> storedKeywordIndex = keywordIndexStore.read("3.27");
        assertThat(storedKeywordIndex).isPresent();
        assertThat(storedKeywordIndex.get().files).isNotEmpty();

        // security-overview.adoc should have keywords like "security", "quarkus"
        assertThat(storedKeywordIndex.get().files)
                .anyMatch(entry -> entry.path.equals("security-overview.adoc")
                        && entry.keywords.stream().anyMatch(k -> k.word.equals("security")));

        // Step 5: Build code sample index from extracted files
        CodeSampleIndex codeSampleIndex = codeSampleIndexer.build("3.27", extractedFiles);
        assertThat(codeSampleIndex.samples).isNotEmpty();

        // Verify code sample index was persisted
        assertThat(codeSampleIndexStore.exists("3.27")).isTrue();

        Optional<CodeSampleIndex> storedCodeSampleIndex = codeSampleIndexStore.read("3.27");
        assertThat(storedCodeSampleIndex).isPresent();
        assertThat(storedCodeSampleIndex.get().samples).isNotEmpty();

        // Verify code samples were extracted with correct metadata
        assertThat(storedCodeSampleIndex.get().samples)
                .anyMatch(sample -> sample.filePath.equals("security-overview.adoc")
                        && sample.language.equals("java")
                        && sample.content.contains("SecurityIdentity"));

        // Verify import boost keywords are present
        assertThat(storedCodeSampleIndex.get().samples)
                .anyMatch(sample -> sample.filePath.equals("security-overview.adoc")
                        && sample.keywords.stream().anyMatch(k -> k.word.equals("inject")));
    }

    @Test
    void warmupExtractsCodeBlocksWithCorrectSectionAssociation() {
        List<String> extractedFiles = zipDownloadService.streamAndExtract("3.27");

        codeSampleIndexer.build("3.27", extractedFiles);

        Optional<CodeSampleIndex> index = codeSampleIndexStore.read("3.27");
        assertThat(index).isPresent();

        // Security doc has 2 code blocks: one in Authentication, one in Authorization
        var securitySamples = index.get().samples.stream()
                .filter(s -> s.filePath.equals("security-overview.adoc"))
                .toList();
        assertThat(securitySamples).hasSize(2);

        assertThat(securitySamples)
                .anyMatch(s -> s.sectionTitle.equals("Authentication")
                        && s.content.contains("SecurityIdentity"));
        assertThat(securitySamples)
                .anyMatch(s -> s.sectionTitle.equals("Authorization")
                        && s.content.contains("RolesAllowed"));

        // Config doc has 1 code block in Properties section
        var configSamples = index.get().samples.stream()
                .filter(s -> s.filePath.equals("config.adoc"))
                .toList();
        assertThat(configSamples).hasSize(1);
        assertThat(configSamples.get(0).sectionTitle).isEqualTo("Properties");
        assertThat(configSamples.get(0).language).isEqualTo("properties");
    }

    @Test
    void warmupKeywordIndexContainsSectionKeywords() {
        List<String> extractedFiles = zipDownloadService.streamAndExtract("3.27");

        keywordIndexer.build("3.27", extractedFiles);

        Optional<KeywordIndex> index = keywordIndexStore.read("3.27");
        assertThat(index).isPresent();

        // security-overview.adoc should have sections with keywords
        var securityEntry = index.get().files.stream()
                .filter(e -> e.path.equals("security-overview.adoc"))
                .findFirst();
        assertThat(securityEntry).isPresent();
        assertThat(securityEntry.get().sections).isNotEmpty();

        // Should have Authentication and Authorization sections
        assertThat(securityEntry.get().sections)
                .anyMatch(s -> s.title.equals("Authentication"));
        assertThat(securityEntry.get().sections)
                .anyMatch(s -> s.title.equals("Authorization"));
    }
}
