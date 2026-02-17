package com.fvd.cache.jobs;

import com.fvd.search.services.SearchService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class CacheRefreshJobIntegrationTest extends AbstractCacheJobIntegrationTest {

    @Inject
    CacheRefreshJob cacheRefreshJob;

    @Inject
    SearchService searchService;

    @Test
    void refreshPreservesExtractedDocsAfterWarmup() {
        // Step 1: Simulate warmup
        simulateWarmup("3.27");

        // Step 2: Simulate refresh
        cacheRefreshJob.refreshVersion("3.27");

        // Step 3: Verify docs are still readable after refresh
        var securityDoc = docStore.read("3.27", "security-overview.adoc");
        assertThat(securityDoc).isPresent()
                .as("Document should still exist after refresh");
    }
}
