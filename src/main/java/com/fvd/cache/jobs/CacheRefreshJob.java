package com.fvd.cache.jobs;

import com.fvd.cache.services.CacheService;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.clients.GithubApiFile;
import com.fvd.github.clients.GithubApiIndex;
import com.fvd.github.services.GitHubService;
import com.fvd.indexs.indexers.CodeSampleIndexer;
import com.fvd.indexs.indexers.ContentIndexer;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.indexs.stores.IndexStore;
import com.fvd.search.services.SearchService;
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
    private final CodeSampleIndexer codeSampleIndexer;
    private final ContentIndexer contentIndexer;
    private final SearchService searchService;
    private final DocParser docParser;

    @Scheduled(every = "${app.refresh.interval:6h}", delayed = "${app.refresh.interval:6h}")
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

            // Rebuild content index with all files
            contentIndexer.build(version, allFilePaths);

            // Invalidate in-memory cache so next search picks up fresh data
            searchService.invalidateCache(version);

        }
        log.info("Cache refresh completed for version {}", version);
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
