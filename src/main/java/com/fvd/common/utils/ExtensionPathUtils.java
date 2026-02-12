package com.fvd.common.utils;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility for grouping documentation file paths by extension.
 */
@UtilityClass
public class ExtensionPathUtils {

    public static final String CORE_EXTENSION_KEY = "quarkus-core";
    public static final String QUARKIVERSE_PREFIX = "quarkiverse/";

    /**
     * Groups file paths into core and quarkiverse extension buckets.
     * Paths starting with "quarkiverse/" are grouped by extension name.
     * All other paths are grouped under "quarkus-core".
     *
     * @param allFiles list of all documentation file paths
     * @return map from extension name to list of file paths
     */
    public static Map<String, List<String>> groupByExtension(List<String> allFiles) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        List<String> coreFiles = new ArrayList<>();
        Map<String, List<String>> quarkiverseGroups = new LinkedHashMap<>();

        for (String path : allFiles) {
            if (path.startsWith(QUARKIVERSE_PREFIX)) {
                String ext = extractExtensionName(path);
                if (ext != null) {
                    quarkiverseGroups.computeIfAbsent(ext, k -> new ArrayList<>()).add(path);
                }
            } else {
                coreFiles.add(path);
            }
        }

        result.put(CORE_EXTENSION_KEY, coreFiles);
        result.putAll(quarkiverseGroups);
        return result;
    }

    /**
     * Groups pre-separated core files and quarkiverse paths into extension buckets.
     *
     * @param coreFiles list of core file paths
     * @param quarkiversePaths list of quarkiverse file paths (format: quarkiverse/ext-name/file.adoc)
     * @return map from extension name to list of file paths
     */
    public static Map<String, List<String>> groupByExtension(List<String> coreFiles, List<String> quarkiversePaths) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put(CORE_EXTENSION_KEY, coreFiles);

        for (String path : quarkiversePaths) {
            String ext = extractExtensionName(path);
            if (ext != null) {
                map.computeIfAbsent(ext, k -> new ArrayList<>()).add(path);
            }
        }

        return map;
    }

    static String extractExtensionName(String path) {
        String[] parts = path.split("/", 3);
        return parts.length >= 2 ? parts[1] : null;
    }
}
