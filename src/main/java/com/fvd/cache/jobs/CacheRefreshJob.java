package com.fvd.cache.jobs;

import com.fvd.cache.services.CacheService;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.clients.GithubApiFile;
import com.fvd.github.clients.GithubApiIndex;
import com.fvd.github.services.GitHubService;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.indexs.stores.IndexStore;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
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

    @Scheduled(every = "${app.refresh.interval:6h}")
    public void refresh() {
        List<String> versions = cacheService.listCachedVersions();
        if (versions.isEmpty()) {
            log.info("No cached versions to refresh");
            return;
        }

        for (String version : versions) {
            try {
                refreshVersion(version);
            } catch (Exception e) {
                log.error("Failed to refresh cache for version {}, keeping existing cache", version, e);
            }
        }
    }

    void refreshVersion(String version) {
        log.info("Refreshing cache for version {}", version);

        List<GithubApiIndex> newIndex = gitHubService.fetchIndex(version);
        Map<String, String> newShaByName = buildShaMap(newIndex);

        Optional<List<GithubApiIndex>> oldIndex = indexStore.read(version);
        Map<String, String> oldShaByName = oldIndex
                .map(this::buildShaMap)
                .orElse(Map.of());

        // Find files that need re-fetching (changed SHA or new files)
        for (Map.Entry<String, String> entry : newShaByName.entrySet()) {
            String fileName = entry.getKey();
            String newSha = entry.getValue();
            String oldSha = oldShaByName.get(fileName);
            if (oldSha == null || !oldSha.equals(newSha)) {
                log.info("Re-fetching changed file: {} (version {})", fileName, version);
                fetchAndCacheDoc(version, fileName);
            }
        }

        // Replace file index with new data
        indexStore.write(version, newIndex);

        // Rebuild keyword index with all files from the new index
        List<String> allFileNames = newIndex.stream()
                .map(e -> e.name)
                .toList();
        keywordIndexer.build(version, allFileNames);

        log.info("Cache refresh completed for version {}", version);
    }

    private void fetchAndCacheDoc(String version, String fileName) {
        GithubApiFile file = gitHubService.fetchFileContent(fileName, version);
        String content = file.decodeContent();
        docStore.write(version, fileName, content);
    }

    private Map<String, String> buildShaMap(List<GithubApiIndex> entries) {
        Map<String, String> shaByName = new HashMap<>();
        for (GithubApiIndex entry : entries) {
            if (entry.name != null && entry.sha != null) {
                shaByName.put(entry.name, entry.sha);
            }
        }
        return shaByName;
    }
}
