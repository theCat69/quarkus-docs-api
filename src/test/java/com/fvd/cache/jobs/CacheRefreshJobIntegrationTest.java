package com.fvd.cache.jobs;

import com.fvd.indexs.indexers.CodeSampleIndex;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.search.services.SearchService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class CacheRefreshJobIntegrationTest extends AbstractCacheJobIntegrationTest {

    @Inject
    CacheRefreshJob cacheRefreshJob;

    @Inject
    SearchService searchService;

    @Test
    void refreshPreservesCodeSampleIndexAfterWarmup() {
        // Step 1: Simulate warmup
        simulateWarmup("3.27");

        // Step 2: Simulate refresh
        cacheRefreshJob.refreshVersion("3.27");

        // Step 3: Verify code samples are preserved after refresh
        Optional<CodeSampleIndex> storedAfterRefresh = codeSampleIndexStore.read("3.27");
        assertThat(storedAfterRefresh).isPresent()
                .as("Code sample index should still exist after refresh");
        assertThat(storedAfterRefresh.get().samples)
                .as("Code samples should not be empty after refresh - the bug causes them to be wiped out")
                .isNotEmpty();

        // Verify the same code sample is still present
        assertThat(storedAfterRefresh.get().samples)
                .anyMatch(s -> s.filePath.equals("security-overview.adoc")
                        && s.language.equals("java")
                        && s.content.contains("SecurityIdentity"));
    }

    @Test
    void refreshPreservesKeywordIndexAfterWarmup() {
        // Step 1: Simulate warmup
        simulateWarmup("3.27");

        // Step 2: Simulate refresh
        cacheRefreshJob.refreshVersion("3.27");

        // Step 3: Verify keyword index is preserved after refresh
        Optional<KeywordIndex> storedAfterRefresh = keywordIndexStore.read("3.27");
        assertThat(storedAfterRefresh).isPresent();
        assertThat(storedAfterRefresh.get().files)
                .as("Keyword index files should not be empty after refresh")
                .isNotEmpty();

        // Verify the same keyword entry is still present (stemmed)
        assertThat(storedAfterRefresh.get().files)
                .anyMatch(f -> f.path.equals("security-overview.adoc")
                        && f.keywords.stream().anyMatch(k -> k.word.equals("secur")));
    }

    @Test
    void refreshedCodeSamplesAreSearchable() {
        // Step 1: Simulate warmup
        simulateWarmup("3.27");

        // Verify search works before refresh
        var resultsBeforeRefresh = searchService.searchCodeSamples(
                "3.27", List.of("security", "inject"), null, null, null, null, null, 10, 0);
        assertThat(resultsBeforeRefresh.items()).isNotEmpty();

        // Step 2: Simulate refresh
        cacheRefreshJob.refreshVersion("3.27");

        // Step 3: Verify search still works after refresh
        var resultsAfterRefresh = searchService.searchCodeSamples(
                "3.27", List.of("security", "inject"), null, null, null, null, null, 10, 0);
        assertThat(resultsAfterRefresh.items())
                .as("Code sample search should return results after refresh")
                .isNotEmpty();
    }
}
