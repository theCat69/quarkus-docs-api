package com.fvd.cache.jobs;

import com.fvd.cache.services.CacheService;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.clients.GithubApiFile;
import com.fvd.github.clients.GithubApiIndex;
import com.fvd.github.services.GitHubService;
import com.fvd.indexs.indexers.CodeSampleIndexer;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.indexs.stores.IndexStore;
import com.fvd.quarkiverse.services.QuarkiverseService;
import com.fvd.search.services.SearchService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CacheRefreshJob {

    private final CacheService cacheService;
    private final GitHubService gitHubService;
    private final IndexStore indexStore;
    private final DocStore docStore;
    private final KeywordIndexer keywordIndexer;
    private final CodeSampleIndexer codeSampleIndexer;
    private final SearchService searchService;
    private final DocParser docParser;
    private final QuarkiverseService quarkiverseService;

    @ConfigProperty(name = "app.quarkiverse.enabled", defaultValue = "false")
    boolean quarkiverseEnabled;

    @Scheduled(every = "${app.refresh.interval:6h}", delayed = "${app.refresh.interval:6h}")
    public void refresh() {
        List<String> versions = cacheService.listCachedVersions();
        if (versions.isEmpty()) {
            log.info("No cached versions to refresh");
            return;
        }

        boolean mainRefreshed = false;

        for (String version : versions) {
            try {
                refreshVersion(version);
                if ("main".equals(version)) {
                    mainRefreshed = true;
                }
            } catch (Exception e) {
                log.error("Failed to refresh cache for version {}, keeping existing cache", version, e);
            }
        }

        // After core refresh, handle quarkiverse for "main"
        if (quarkiverseEnabled && mainRefreshed) {
            refreshQuarkiverse();
        }
    }

    void refreshVersion(String version) {
        log.info("Refreshing cache for version {}", version);

        List<GithubApiIndex> newIndex = gitHubService.fetchIndex(version);
        Map<String, String> newShaByPath = buildShaMap(newIndex, version);

        Optional<List<GithubApiIndex>> oldIndex = indexStore.read(version);
        Map<String, String> oldShaByPath = oldIndex
                .map(idx -> buildShaMap(idx, version))
                .orElse(Map.of());

        // Find files that need re-fetching (changed SHA or new files)
        for (Map.Entry<String, String> entry : newShaByPath.entrySet()) {
            String filePath = entry.getKey();
            String newSha = entry.getValue();
            String oldSha = oldShaByPath.get(filePath);
            if (oldSha == null || !oldSha.equals(newSha)) {
                log.info("Re-fetching changed file: {} (version {})", filePath, version);
                log.debug("Old sha : {}, new sha : {}", oldSha, newSha);
                fetchAndCacheDoc(version, filePath);
            }
        }

        if(!newIndex.isEmpty()) {
            log.info("Should rebuild index stores");

            // Replace file index with new data
            indexStore.write(version, newIndex);

            // Strip the docs prefix from GitHub API paths to get relative paths
            // consistent with what ZipDownloadService produces during warmup.
            // GitHub API returns paths like "_versions/3.27/guides/file.adoc"
            // but DocStore and indexers expect relative paths like "file.adoc".
            List<String> allFilePaths = newIndex.stream()
                    .map(e -> stripDocsPrefix(e.path, version))
                    .toList();
            keywordIndexer.build(version, allFilePaths);

            // Rebuild code sample index with all files
            codeSampleIndexer.build(version, allFilePaths);

            // Invalidate in-memory cache so next search picks up fresh data
            searchService.invalidateCache(version);

        }
        log.info("Cache refresh completed for version {}", version);
    }

    private void refreshQuarkiverse() {
        try {
            boolean anyChanges = quarkiverseService.refreshAll();
            if (!anyChanges) {
                log.info("No quarkiverse changes detected, skipping main index rebuild");
                return;
            }

            log.info("Quarkiverse changes detected, rebuilding main indexes with merged data");

            // Get all files currently on disk for "main" (includes quarkiverse)
            List<String> allFiles = docStore.listDocFiles("main");
            Map<String, List<String>> filePathsByExtension = buildExtensionMap(allFiles);

            keywordIndexer.build("main", filePathsByExtension);
            codeSampleIndexer.build("main", filePathsByExtension);

            searchService.invalidateCache("main");

            log.info("Main indexes rebuilt with quarkiverse data ({} extensions)",
                    filePathsByExtension.size());
        } catch (Exception e) {
            log.error("Failed to refresh quarkiverse docs, main core indexes remain intact", e);
        }
    }

    static Map<String, List<String>> buildExtensionMap(List<String> allFiles) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        List<String> coreFiles = new ArrayList<>();
        Map<String, List<String>> quarkiverseGroups = new LinkedHashMap<>();

        for (String path : allFiles) {
            if (path.startsWith("quarkiverse/")) {
                String[] parts = path.split("/", 3);
                if (parts.length >= 2) {
                    String extensionName = parts[1];
                    quarkiverseGroups.computeIfAbsent(extensionName, k -> new ArrayList<>()).add(path);
                }
            } else {
                coreFiles.add(path);
            }
        }

        map.put("quarkus-core", coreFiles);
        map.putAll(quarkiverseGroups);
        return map;
    }

    private void fetchAndCacheDoc(String version, String filePath) {
        GithubApiFile file = gitHubService.fetchFileContent(filePath, version);
        String content = file.decodeContent();
        // Strip the docs prefix so the file is stored at the same relative path
        // that ZipDownloadService uses during warmup
        docStore.write(version, stripDocsPrefix(filePath, version), content);
    }

    String stripDocsPrefix(String path, String version) {
        String prefix = docParser.docsPrefix(version);
        if (path.startsWith(prefix)) {
            return path.substring(prefix.length());
        }
        return path;
    }

    private Map<String, String> buildShaMap(List<GithubApiIndex> entries, String version) {
        Map<String, String> shaByPath = new HashMap<>();
        for (GithubApiIndex entry : entries) {
            if (entry.path != null && entry.sha != null && entry.name.endsWith(docParser.fileSuffix())) {
                shaByPath.put(entry.path, entry.sha);
            }
        }
        return shaByPath;
    }
}
