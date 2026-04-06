package com.fvd.api.services;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fvd.api.dto.CatalogResponse;
import com.fvd.api.dto.ExtensionInfo;
import com.fvd.api.dto.SubjectInfo;
import com.fvd.cache.services.CacheService;
import com.fvd.indexs.stores.DocChunkStore;

/**
 * Service for retrieving catalog information including subjects, extensions, and versions.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CatalogService {

    private final DocChunkStore docChunkStore;
    private final CacheService cacheService;

    private static final int MAX_CATALOG_CACHE_SIZE = 100;

    private final Map<String, CatalogResponse> catalogCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CatalogResponse> eldest) {
                    return size() > MAX_CATALOG_CACHE_SIZE;
                }
            });

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
        Map<String, Integer> topicsWithCounts = docChunkStore.findDistinctTopicsWithDocCount(version);

        List<SubjectInfo> subjects = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : topicsWithCounts.entrySet()) {
            String name = entry.getKey();
            String displayName = formatDisplayName(name);
            subjects.add(new SubjectInfo(name, displayName, "", entry.getValue(), List.of()));
        }
        return subjects;
    }

    private List<ExtensionInfo> buildExtensionList(String version) {
        Map<String, Integer> extensionsWithCounts = docChunkStore.findDistinctExtensionsWithDocCount(version);

        List<ExtensionInfo> extensions = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : extensionsWithCounts.entrySet()) {
            String name = entry.getKey();
            String displayName = formatExtensionDisplayName(name);
            String description = readExtensionDescription(name, version);
            extensions.add(new ExtensionInfo(name, displayName, description, entry.getValue(), List.of()));
        }

        // Sort by doc count descending, then by name
        extensions.sort(Comparator.comparingInt((ExtensionInfo e) -> e.docCount).reversed()
                .thenComparing(e -> e.name));

        return extensions;
    }

    private String readExtensionDescription(String extensionName, String version) {
        if ("quarkus-core".equals(extensionName)) {
            return "Core Quarkus framework documentation";
        }
        // Prevent path traversal — extension names should be alphanumeric with dashes only
        if (extensionName == null || !extensionName.matches("^[a-zA-Z0-9][a-zA-Z0-9._-]*$")) {
            log.warn("Invalid extension name, skipping description lookup: {}", extensionName);
            return "";
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

    private String formatDisplayName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        return capitalizeHyphenatedWords(name);
    }

    private String formatExtensionDisplayName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        // Convert "quarkus-resteasy-reactive" to "Resteasy Reactive"
        String withoutPrefix = name.startsWith("quarkus-") ? name.substring(8) : name;
        return capitalizeHyphenatedWords(withoutPrefix);
    }

    private String capitalizeHyphenatedWords(String hyphenated) {
        String[] parts = hyphenated.split("-");
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
