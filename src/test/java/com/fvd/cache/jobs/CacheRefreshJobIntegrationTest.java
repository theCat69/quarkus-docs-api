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
import com.fvd.search.services.SearchService;
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
class CacheRefreshJobIntegrationTest {

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

    @Inject
    CacheRefreshJob cacheRefreshJob;

    @Inject
    SearchService searchService;

    @BeforeEach
    void cleanTestCache() throws IOException {
        var cachePath = Path.of("build/test-cache").toFile();
        if (cachePath.exists()) {
            FileUtils.cleanDirectory(cachePath);
        }
        schemaInitializer.initSchema();
    }

    @Test
    void refreshPreservesCodeSampleIndexAfterWarmup() {
        // Step 1: Simulate warmup - download zip and build indexes (like CacheWarmupJob does)
        List<String> extractedFiles = zipDownloadService.streamAndExtract("3.27");
        assertThat(extractedFiles).containsExactlyInAnyOrder("security-overview.adoc", "config.adoc");

        indexService.getOrFetchIndex("3.27");
        keywordIndexer.build("3.27", extractedFiles);
        CodeSampleIndex warmupIndex = codeSampleIndexer.build("3.27", extractedFiles);

        // Verify code samples exist after warmup
        assertThat(warmupIndex.samples).isNotEmpty();
        Optional<CodeSampleIndex> storedAfterWarmup = codeSampleIndexStore.read("3.27");
        assertThat(storedAfterWarmup).isPresent();
        int warmupSampleCount = storedAfterWarmup.get().samples.size();
        assertThat(warmupSampleCount).isGreaterThan(0);

        // Verify specific code sample content from warmup
        assertThat(storedAfterWarmup.get().samples)
                .anyMatch(s -> s.filePath.equals("security-overview.adoc")
                        && s.language.equals("java")
                        && s.content.contains("SecurityIdentity"));

        // Step 2: Simulate refresh - call refreshVersion (like CacheRefreshJob does)
        // This uses GitHub API index paths (docs/src/main/asciidoc/...) instead of relative paths
        cacheRefreshJob.refreshVersion("3.27");

        // Step 3: Verify code samples are preserved after refresh
        Optional<CodeSampleIndex> storedAfterRefresh = codeSampleIndexStore.read("3.27");
        assertThat(storedAfterRefresh).isPresent()
                .as("Code sample index should still exist after refresh");
        assertThat(storedAfterRefresh.get().samples)
                .as("Code samples should not be empty after refresh - the bug causes them to be wiped out")
                .isNotEmpty();
        assertThat(storedAfterRefresh.get().samples).hasSizeGreaterThanOrEqualTo(warmupSampleCount);

        // Verify the same code sample is still present
        assertThat(storedAfterRefresh.get().samples)
                .anyMatch(s -> s.filePath.equals("security-overview.adoc")
                        && s.language.equals("java")
                        && s.content.contains("SecurityIdentity"));
    }

    @Test
    void refreshPreservesKeywordIndexAfterWarmup() {
        // Step 1: Simulate warmup
        List<String> extractedFiles = zipDownloadService.streamAndExtract("3.27");
        indexService.getOrFetchIndex("3.27");
        keywordIndexer.build("3.27", extractedFiles);
        codeSampleIndexer.build("3.27", extractedFiles);

        Optional<KeywordIndex> storedAfterWarmup = keywordIndexStore.read("3.27");
        assertThat(storedAfterWarmup).isPresent();
        int warmupFileCount = storedAfterWarmup.get().files.size();
        assertThat(warmupFileCount).isGreaterThan(0);

        // Verify specific keyword content
        assertThat(storedAfterWarmup.get().files)
                .anyMatch(f -> f.path.equals("security-overview.adoc")
                        && f.keywords.stream().anyMatch(k -> k.word.equals("security")));

        // Step 2: Simulate refresh
        cacheRefreshJob.refreshVersion("3.27");

        // Step 3: Verify keyword index is preserved after refresh
        Optional<KeywordIndex> storedAfterRefresh = keywordIndexStore.read("3.27");
        assertThat(storedAfterRefresh).isPresent();
        assertThat(storedAfterRefresh.get().files)
                .as("Keyword index files should not be empty after refresh")
                .isNotEmpty();
        assertThat(storedAfterRefresh.get().files).hasSizeGreaterThanOrEqualTo(warmupFileCount);

        // Verify the same keyword entry is still present
        assertThat(storedAfterRefresh.get().files)
                .anyMatch(f -> f.path.equals("security-overview.adoc")
                        && f.keywords.stream().anyMatch(k -> k.word.equals("security")));
    }

    @Test
    void refreshedCodeSamplesAreSearchable() {
        // Step 1: Simulate warmup
        List<String> extractedFiles = zipDownloadService.streamAndExtract("3.27");
        indexService.getOrFetchIndex("3.27");
        keywordIndexer.build("3.27", extractedFiles);
        codeSampleIndexer.build("3.27", extractedFiles);

        // Verify search works before refresh
        var resultsBeforeRefresh = searchService.searchCodeSamples(
                "3.27", List.of("security", "inject"), null, null, 10, 0);
        assertThat(resultsBeforeRefresh.items()).isNotEmpty();

        // Step 2: Simulate refresh
        cacheRefreshJob.refreshVersion("3.27");

        // Step 3: Verify search still works after refresh
        var resultsAfterRefresh = searchService.searchCodeSamples(
                "3.27", List.of("security", "inject"), null, null, 10, 0);
        assertThat(resultsAfterRefresh.items())
                .as("Code sample search should return results after refresh")
                .isNotEmpty();
    }
}
