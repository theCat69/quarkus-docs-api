package com.fvd.api.services;

import com.fvd.api.dto.CatalogResponse;
import com.fvd.api.dto.ExtensionInfo;
import com.fvd.api.dto.SubjectInfo;
import com.fvd.asciidocs.model.DocumentMetadata;
import com.fvd.cache.services.CacheService;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.subject.Subject;
import com.fvd.subject.services.MetadataAwareSubjectResolver;
import com.fvd.subject.services.SubjectDeriver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for retrieving catalog information including subjects, extensions, and versions.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CatalogService {

    private static final int MAX_EXTENSION_KEYWORDS = 15;

    private final SubjectDeriver subjectDeriver;
    private final MetadataAwareSubjectResolver metadataResolver;
    private final KeywordIndexStore keywordIndexStore;
    private final CacheService cacheService;

    private final Map<String, CatalogResponse> catalogCache = new ConcurrentHashMap<>();

    /**
     * Gets the catalog for a specific version.
     * Results are cached per version.
     *
     * @param version the documentation version
     * @return the catalog response
     */
    public CatalogResponse getCatalog(String version) {
        CatalogResponse cached = catalogCache.get(version);
        if (cached != null) {
            return cached;
        }

        CatalogResponse catalog = buildCatalog(version);
        catalogCache.put(version, catalog);
        return catalog;
    }

    /**
     * Invalidates the catalog cache for a specific version.
     *
     * @param version the version to invalidate
     */
    public void invalidateCache(String version) {
        catalogCache.remove(version);
    }

    private CatalogResponse buildCatalog(String version) {
        List<SubjectInfo> subjects = buildSubjectList(version);
        List<ExtensionInfo> extensions = buildExtensionList(version);
        List<String> versions = cacheService.listCachedVersions();

        return new CatalogResponse(subjects, extensions, versions);
    }

    private List<SubjectInfo> buildSubjectList(String version) {
        // Reset doc counts and re-derive from current index
        subjectDeriver.resetDocCounts();

        Optional<KeywordIndex> indexOpt = keywordIndexStore.read(version);
        if (indexOpt.isEmpty()) {
            // Return subjects with zero doc counts
            return subjectDeriver.getAllSubjects().stream()
                    .map(this::toSubjectInfo)
                    .toList();
        }

        KeywordIndex index = indexOpt.get();
        
        // Derive subjects for all indexed files and update counts
        Map<String, DocumentMetadata> metadataMap = metadataResolver.loadMetadataMap(version);
        for (FileKeywordEntry file : index.files) {
            String subject = metadataResolver.resolveSubject(file.path, metadataMap);
            subjectDeriver.recordDocument(subject);
        }

        return subjectDeriver.getAllSubjects().stream()
                .map(this::toSubjectInfo)
                .toList();
    }

    private List<ExtensionInfo> buildExtensionList(String version) {
        Optional<KeywordIndex> indexOpt = keywordIndexStore.read(version);
        if (indexOpt.isEmpty()) {
            return List.of();
        }

        KeywordIndex index = indexOpt.get();
        Map<String, Integer> extensionDocCounts = new HashMap<>();
        Map<String, Map<String, Integer>> extensionKeywordScores = new HashMap<>();

        for (FileKeywordEntry file : index.files) {
            String ext = file.extension != null ? file.extension : "quarkus-core";
            extensionDocCounts.merge(ext, 1, Integer::sum);

            if (file.keywords != null) {
                Map<String, Integer> keywordScores = extensionKeywordScores
                        .computeIfAbsent(ext, k -> new HashMap<>());
                for (KeywordScore ks : file.keywords) {
                    keywordScores.merge(ks.word, ks.score, Integer::sum);
                }
            }
        }

        List<ExtensionInfo> extensions = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : extensionDocCounts.entrySet()) {
            String name = entry.getKey();
            String displayName = formatExtensionDisplayName(name);
            String description = readExtensionDescription(name, version);
            List<String> keywords = getTopKeywords(extensionKeywordScores.get(name));
            extensions.add(new ExtensionInfo(name, displayName, description, entry.getValue(), keywords));
        }

        // Sort by doc count descending, then by name
        extensions.sort((a, b) -> {
            int cmp = Integer.compare(b.docCount, a.docCount);
            return cmp != 0 ? cmp : a.name.compareTo(b.name);
        });

        return extensions;
    }

    private List<String> getTopKeywords(Map<String, Integer> keywordScores) {
        if (keywordScores == null || keywordScores.isEmpty()) {
            return List.of();
        }
        return keywordScores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(MAX_EXTENSION_KEYWORDS)
                .map(Map.Entry::getKey)
                .toList();
    }

    private String readExtensionDescription(String extensionName, String version) {
        if ("quarkus-core".equals(extensionName)) {
            return "Core Quarkus framework documentation";
        }
        try {
            Path titleFile = cacheService.versionDir(version)
                    .resolve("docs").resolve("quarkiverse")
                    .resolve(extensionName).resolve(".extension-title");
            if (Files.exists(titleFile)) {
                return Files.readString(titleFile).trim();
            }
        } catch (Exception e) {
            log.warn("Failed to read extension title for {}: {}", extensionName, e.getMessage());
        }
        return "";
    }

    private SubjectInfo toSubjectInfo(Subject subject) {
        return new SubjectInfo(
                subject.name(),
                subject.displayName(),
                subject.description(),
                subject.docCount(),
                subject.keywords()
        );
    }

    private String formatExtensionDisplayName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        // Convert "quarkus-resteasy-reactive" to "RESTEasy Reactive"
        String withoutPrefix = name.startsWith("quarkus-") ? name.substring(8) : name;
        String[] parts = withoutPrefix.split("-");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append(" ");
                }
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            }
        }
        return sb.toString();
    }
}
