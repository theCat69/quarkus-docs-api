package com.fvd.cache.jobs;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class CacheWarmupJobIntegrationTest extends AbstractCacheJobIntegrationTest {

    @Test
    void warmupExtractsDocsFromZip() {
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
    }
}
