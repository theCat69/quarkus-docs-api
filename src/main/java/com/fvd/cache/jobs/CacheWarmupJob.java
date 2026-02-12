package com.fvd.cache.jobs;

import com.fvd.cache.services.CacheService;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.services.ZipDownloadService;
import com.fvd.indexs.indexers.CodeSampleIndexer;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.indexs.services.IndexService;
import com.fvd.common.utils.ExtensionPathUtils;
import com.fvd.quarkiverse.services.QuarkiverseService;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;
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
    private final CacheService cacheService;
    private final QuarkiverseService quarkiverseService;

    @ConfigProperty(name = "app.versions")
    Optional<List<String>> configuredVersions;
    @ConfigProperty(name = "app.cache-warmup.full-reset")
    Optional<Boolean> fullReset;
    @ConfigProperty(name = "app.quarkiverse.enabled", defaultValue = "false")
    boolean quarkiverseEnabled;

    void onStartup(@Observes @Priority(200) StartupEvent event) {
        if (configuredVersions.isEmpty() || configuredVersions.get().isEmpty()) {
            log.info("No versions configured for warmup (app.versions is empty)");
            return;
        }

        List<String> versions = configuredVersions.get();
        log.info("Starting cache warmup for versions: {}", versions);

        fullReset.ifPresent((bool) -> { if(bool) cacheService.deleteCache(); });

        // Filter out versions that are already cached
        List<String> versionsToWarm = versions.stream()
                .filter(v -> !docStore.docsExist(v))
                .toList();

        if (versionsToWarm.isEmpty()) {
            log.info("All versions already cached, skipping warmup");
            return;
        }

        log.info("Downloading zip and extracting {} versions: {}", versionsToWarm.size(), versionsToWarm);

        try {
            Map<String, List<String>> extractedByVersion = zipDownloadService.streamAndExtractAll(versionsToWarm);

            for (Map.Entry<String, List<String>> entry : extractedByVersion.entrySet()) {
                String version = entry.getKey();
                List<String> extractedFiles = entry.getValue();

                try {
                    log.info("Extracted {} files for version {}", extractedFiles.size(), version);

                    indexService.getOrFetchIndex(version);
                    log.info("Index fetched for version {}", version);

                    if ("main".equals(version) && quarkiverseEnabled) {
                        // Defer index build for "main" until after quarkiverse extraction
                        continue;
                    }

                    buildIndexes(version, extractedFiles);
                } catch (Exception e) {
                    log.error("Failed to build indexes for version {}, skipping", version, e);
                }
            }

            // Handle quarkiverse for "main" version
            if (quarkiverseEnabled && extractedByVersion.containsKey("main")) {
                List<String> mainCoreFiles = extractedByVersion.get("main");
                buildMainWithQuarkiverse(mainCoreFiles);
            }
        } catch (Exception e) {
            log.error("Failed to download and extract zip for warmup", e);
        }

        log.info("Cache warmup completed");
    }

    private void buildMainWithQuarkiverse(List<String> coreFiles) {
        List<String> quarkiversePaths;
        try {
            quarkiversePaths = quarkiverseService.fetchAndExtractAll();
        } catch (Exception e) {
            log.error("Failed to fetch quarkiverse docs, building main with core only", e);
            buildIndexes("main", coreFiles);
            return;
        }

        if (quarkiversePaths.isEmpty()) {
            log.info("No quarkiverse docs found, building main with core only");
            buildIndexes("main", coreFiles);
            return;
        }

        Map<String, List<String>> filePathsByExtension = ExtensionPathUtils.groupByExtension(coreFiles, quarkiversePaths);
        log.info("Building main indexes with {} core files and {} quarkiverse files across {} extensions",
                coreFiles.size(), quarkiversePaths.size(), filePathsByExtension.size() - 1);

        keywordIndexer.build("main", filePathsByExtension);
        log.info("Keyword index built for version main (merged)");

        codeSampleIndexer.build("main", filePathsByExtension);
        log.info("Code sample index built for version main (merged)");
    }

    private void buildIndexes(String version, List<String> extractedFiles) {
        keywordIndexer.build(version, extractedFiles);
        log.info("Keyword index built for version {}", version);

        codeSampleIndexer.build(version, extractedFiles);
        log.info("Code sample index built for version {}", version);
    }
}
