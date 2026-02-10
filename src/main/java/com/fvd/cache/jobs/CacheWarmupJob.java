package com.fvd.cache.jobs;

import com.fvd.cache.services.CacheService;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.services.ZipDownloadService;
import com.fvd.indexs.indexers.CodeSampleIndexer;
import com.fvd.indexs.indexers.ContentIndexer;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.indexs.services.IndexService;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
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
    private final CodeSampleIndexer codeSampleIndexer;
    private final ContentIndexer contentIndexer;
    private final CacheService cacheService;

    @ConfigProperty(name = "app.versions")
    Optional<List<String>> configuredVersions;
    @ConfigProperty(name = "app.cache-warmup.full-reset")
    Optional<Boolean> fullReset;

    void onStartup(@Observes @Priority(200) StartupEvent event) {
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
        fullReset.ifPresent((bool) -> { if(bool) cacheService.deleteCache(); });
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

        codeSampleIndexer.build(version, extractedFiles);
        log.info("Code sample index built for version {}", version);

        contentIndexer.build(version, extractedFiles);
        log.info("Content index built for version {}", version);
    }
}
