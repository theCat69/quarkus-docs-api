package com.fvd.cache.jobs;

import com.fvd.docs.stores.DocStore;
import com.fvd.github.services.ZipDownloadService;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.indexs.services.IndexService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CacheWarmupJob {

    private final DocStore docStore;
    private final ZipDownloadService zipDownloadService;
    private final IndexService indexService;
    private final KeywordIndexer keywordIndexer;

    @ConfigProperty(name = "app.versions")
    Optional<List<String>> configuredVersions;

    void onStartup(@Observes StartupEvent event) {
        if (configuredVersions.isEmpty() || configuredVersions.get().isEmpty()) {
            log.info("No versions configured for warmup (app.versions is empty)");
            return;
        }

        List<String> versions = configuredVersions.get();
        log.info("Starting cache warmup for versions: {}", versions);

        for (String version : versions) {
            try {
                warmupVersion(version);
            } catch (Exception e) {
                log.error("Failed to warm up cache for version {}, skipping", version, e);
            }
        }

        log.info("Cache warmup completed");
    }

    private void warmupVersion(String version) {
        if (docStore.docsExist(version)) {
            log.info("Version {} already cached, skipping warmup", version);
            return;
        }

        log.info("Warming up version {}", version);

        List<String> extractedFiles = zipDownloadService.streamAndExtract(version);
        log.info("Extracted {} files for version {}", extractedFiles.size(), version);

        indexService.getOrFetchIndex(version);
        log.info("Index fetched for version {}", version);

        keywordIndexer.build(version, extractedFiles);
        log.info("Keyword index built for version {}", version);
    }
}
