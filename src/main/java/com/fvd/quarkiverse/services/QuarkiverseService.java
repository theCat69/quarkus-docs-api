package com.fvd.quarkiverse.services;

import com.fvd.cache.services.CacheService;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.clients.GithubApiFile;
import com.fvd.github.clients.GithubApiIndex;
import com.fvd.github.services.GitHubService;
import com.fvd.indexs.stores.IndexStore;
import com.fvd.quarkiverse.parser.AntoraPlaybookParser;
import com.fvd.quarkiverse.parser.ResolvedContentSource;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class QuarkiverseService {

    private final GitHubService gitHubService;
    private final CacheService cacheService;
    private final IndexStore indexStore;
    private final AntoraPlaybookParser parser;
    private final QuarkiverseZipExtractor zipExtractor;
    private final String playbookRepo;
    private final String playbookBranch;
    private final int downloadConcurrency;

    public QuarkiverseService(
            GitHubService gitHubService,
            CacheService cacheService,
            IndexStore indexStore,
            AntoraPlaybookParser parser,
            QuarkiverseZipExtractor zipExtractor,
            @ConfigProperty(name = "app.quarkiverse.playbook-repo", defaultValue = "quarkiverse/quarkiverse-docs")
            String playbookRepo,
            @ConfigProperty(name = "app.quarkiverse.playbook-branch", defaultValue = "main")
            String playbookBranch,
            @ConfigProperty(name = "app.quarkiverse.download-concurrency", defaultValue = "4")
            int downloadConcurrency) {
        this.gitHubService = gitHubService;
        this.cacheService = cacheService;
        this.indexStore = indexStore;
        this.parser = parser;
        this.zipExtractor = zipExtractor;
        this.playbookRepo = playbookRepo;
        this.playbookBranch = playbookBranch;
        this.downloadConcurrency = downloadConcurrency;
    }

    public List<String> fetchAndExtractAll() {
        List<ResolvedContentSource> sources = fetchAndParsePlaybook();
        if (sources.isEmpty()) {
            return List.of();
        }

        log.info("Found {} quarkiverse content sources to process", sources.size());
        List<String> allExtractedPaths = new ArrayList<>();

        for (int i = 0; i < sources.size(); i++) {
            ResolvedContentSource source = sources.get(i);
            log.info("Processing extension {}/{}: {}", i + 1, sources.size(), source.extensionName());
            try {
                List<String> paths = processExtension(source);
                allExtractedPaths.addAll(paths);
            } catch (Exception e) {
                log.error("Failed to process extension: {}, continuing with next", source.extensionName(), e);
            }
        }

        log.info("Quarkiverse extraction complete: {} total files from {} extensions",
                allExtractedPaths.size(), sources.size());
        return allExtractedPaths;
    }

    public boolean refreshAll() {
        List<ResolvedContentSource> sources = fetchAndParsePlaybook();
        if (sources.isEmpty()) {
            return false;
        }

        boolean anyChanges = false;

        for (int i = 0; i < sources.size(); i++) {
            ResolvedContentSource source = sources.get(i);
            try {
                boolean changed = refreshExtension(source);
                if (changed) {
                    anyChanges = true;
                }
            } catch (Exception e) {
                log.error("Failed to refresh extension: {}, continuing with next", source.extensionName(), e);
            }
        }

        return anyChanges;
    }

    private List<ResolvedContentSource> fetchAndParsePlaybook() {
        try {
            String[] repoParts = playbookRepo.split("/");
            String owner = repoParts[0];
            String repo = repoParts[1];

            GithubApiFile playbookFile = gitHubService.fetchFileContentForRepo(
                    owner, repo, "antora-playbook.yml", playbookBranch);
            String yamlContent = playbookFile.decodeContent();
            return parser.parse(yamlContent);
        } catch (Exception e) {
            log.error("Failed to fetch or parse antora-playbook.yml", e);
            return List.of();
        }
    }

    private List<String> processExtension(ResolvedContentSource source) {
        try (InputStream zipStream = gitHubService.fetchZipStreamForRepo(
                source.org(), source.repo(), source.branch())) {
            return zipExtractor.extractDocs(zipStream, source.extensionName(),
                    source.startPath(), cacheService);
        } catch (Exception e) {
            throw new RuntimeException("Failed to download/extract extension: " + source.extensionName(), e);
        }
    }

    private boolean refreshExtension(ResolvedContentSource source) {
        String compositeKey = "quarkiverse/" + source.extensionName();
        String docsPath = buildDocsPath(source.startPath());

        List<GithubApiIndex> newIndex;
        try {
            newIndex = gitHubService.fetchIndexForRepo(
                    source.org(), source.repo(), docsPath, source.branch());
        } catch (Exception e) {
            log.warn("Failed to fetch index for extension: {}, skipping", source.extensionName(), e);
            return false;
        }

        Map<String, String> newShaByPath = buildShaMap(newIndex);
        Optional<List<GithubApiIndex>> oldIndexOpt = indexStore.read(compositeKey);
        Map<String, String> oldShaByPath = oldIndexOpt
                .map(this::buildShaMap)
                .orElse(Map.of());

        boolean hasChanges = false;

        for (Map.Entry<String, String> entry : newShaByPath.entrySet()) {
            String filePath = entry.getKey();
            String newSha = entry.getValue();
            String oldSha = oldShaByPath.get(filePath);

            if (oldSha == null || !oldSha.equals(newSha)) {
                hasChanges = true;
                try {
                    GithubApiFile file = gitHubService.fetchFileContentForRepo(
                            source.org(), source.repo(), filePath, source.branch());
                    String content = file.decodeContent();
                    String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
                    String namespacedPath = "quarkiverse/" + source.extensionName() + "/" + fileName;

                    // Write directly to cache using DocStore pattern
                    java.nio.file.Path outputFile = cacheService.versionDir("main")
                            .resolve("docs").resolve(namespacedPath);
                    java.nio.file.Files.createDirectories(outputFile.getParent());
                    java.nio.file.Files.writeString(outputFile, content);

                    log.info("Updated file: {} for extension {}", fileName, source.extensionName());
                } catch (Exception e) {
                    log.error("Failed to fetch changed file: {} for extension {}",
                            filePath, source.extensionName(), e);
                }
            }
        }

        // Always update the stored index
        indexStore.write(compositeKey, newIndex);

        return hasChanges;
    }

    private String buildDocsPath(String startPath) {
        if (startPath == null || startPath.isEmpty()) {
            return "modules/ROOT/pages";
        }
        return startPath + "/modules/ROOT/pages";
    }

    private Map<String, String> buildShaMap(List<GithubApiIndex> entries) {
        Map<String, String> shaByPath = new HashMap<>();
        for (GithubApiIndex entry : entries) {
            if (entry.path != null && entry.sha != null) {
                shaByPath.put(entry.path, entry.sha);
            }
        }
        return shaByPath;
    }
}
