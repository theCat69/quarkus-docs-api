package com.fvd.cache.jobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fvd.cache.services.CacheService;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.exceptions.UpstreamException;
import com.fvd.github.services.GitHubService;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.indexs.stores.IndexStore;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CacheRefreshJob {

    private final CacheService cacheService;
    private final GitHubService gitHubService;
    private final IndexStore indexStore;
    private final DocStore docStore;
    private final KeywordIndexer keywordIndexer;
    private final ObjectMapper objectMapper;

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

        String newIndexJson = gitHubService.fetchIndex(version);
        List<Map<String, Object>> newEntries = parseIndex(newIndexJson);
        Map<String, String> newShaByName = buildShaMap(newEntries);

        Optional<String> oldIndexJson = indexStore.readRaw(version);
        Map<String, String> oldShaByName = oldIndexJson
                .map(this::parseIndex)
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
        indexStore.writeRaw(version, newIndexJson);

        // Rebuild keyword index with all files from the new index
        List<String> allFileNames = newEntries.stream()
                .map(e -> (String) e.get("name"))
                .toList();
        keywordIndexer.build(version, allFileNames);

        log.info("Cache refresh completed for version {}", version);
    }

    private void fetchAndCacheDoc(String version, String fileName) {
        String jsonResponse = gitHubService.fetchFileContent(fileName, version);
        String content = decodeContent(jsonResponse, fileName);
        docStore.write(version, fileName, content);
    }

    private String decodeContent(String jsonResponse, String fileName) {
        try {
            JsonNode node = objectMapper.readTree(jsonResponse);
            String encoding = node.has("encoding") ? node.get("encoding").asText() : "";
            String rawContent = node.has("content") ? node.get("content").asText() : "";
            if ("base64".equals(encoding)) {
                String cleaned = rawContent.replaceAll("\\s", "");
                return new String(Base64.getDecoder().decode(cleaned));
            }
            return rawContent;
        } catch (Exception e) {
            throw new UpstreamException("Failed to decode content for: " + fileName, e);
        }
    }

    private List<Map<String, Object>> parseIndex(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse index JSON", e);
        }
    }

    private Map<String, String> buildShaMap(List<Map<String, Object>> entries) {
        Map<String, String> shaByName = new HashMap<>();
        for (Map<String, Object> entry : entries) {
            String name = (String) entry.get("name");
            String sha = (String) entry.get("sha");
            if (name != null && sha != null) {
                shaByName.put(name, sha);
            }
        }
        return shaByName;
    }
}
