package com.fvd.quarkiverse.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fvd.cache.services.CacheService;
import com.fvd.quarkiverse.models.AntoraComponentDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@ApplicationScoped
public class QuarkiverseZipExtractor {

    private static final String ROOT_PAGES_SEGMENT = "/modules/ROOT/pages/";
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public List<String> extractDocs(InputStream zipStream, String extensionName, String startPath,
                                    CacheService cacheService) {
        List<String> extractedPaths = new ArrayList<>();
        Path outputDir = cacheService.versionDir("main").resolve("docs").resolve("quarkiverse").resolve(extensionName);

        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create quarkiverse output dir: " + outputDir, e);
        }

        String pagesPrefix = buildPagesPrefix(startPath);
        String antoraYmlPath = buildAntoraYmlPath(startPath);

        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                String entryName = entry.getName();

                // Check for antora.yml at startPath root
                String pathAfterPrefix = entryName.substring(entryName.indexOf('/') + 1);
                if (pathAfterPrefix.equals(antoraYmlPath)) {
                    extractTitle(zis, outputDir);
                    zis.closeEntry();
                    continue;
                }

                String relativePath = extractRelativePath(entryName, pagesPrefix, startPath);

                if (relativePath != null) {
                    if (!relativePath.endsWith(".adoc")) {
                        zis.closeEntry();
                        continue;
                    }

                    // Check for non-ROOT modules and warn
                    if (hasNonRootModule(entryName, startPath)) {
                        log.warn("Skipping non-ROOT module file: {}", entryName);
                        zis.closeEntry();
                        continue;
                    }

                    byte[] content = zis.readAllBytes();
                    Path targetFile = outputDir.resolve(relativePath);
                    Files.createDirectories(targetFile.getParent());
                    Files.writeString(targetFile, new String(content));

                    String namespacedPath = "quarkiverse/" + extensionName + "/" + relativePath;
                    extractedPaths.add(namespacedPath);
                }

                zis.closeEntry();
            }
        } catch (IOException e) {
            log.error("Failed to extract docs for extension: {}", extensionName, e);
        }

        return extractedPaths;
    }

    private String buildPagesPrefix(String startPath) {
        if (startPath == null || startPath.isEmpty()) {
            return "modules/ROOT/pages/";
        }
        String normalizedPath = startPath.endsWith("/") ? startPath : startPath + "/";
        return normalizedPath + "modules/ROOT/pages/";
    }

    String extractRelativePath(String entryName, String pagesPrefix, String startPath) {
        // GitHub zip entries have a prefix like "repo-branch/" which we need to skip
        int firstSlash = entryName.indexOf('/');
        if (firstSlash < 0) {
            return null;
        }
        String pathAfterRoot = entryName.substring(firstSlash + 1);

        // Check if it matches the expected pages path
        String fullPagesPrefix = buildPagesPrefix(startPath);
        if (!pathAfterRoot.startsWith(fullPagesPrefix)) {
            return null;
        }

        return pathAfterRoot.substring(fullPagesPrefix.length());
    }

    private boolean hasNonRootModule(String entryName, String startPath) {
        int firstSlash = entryName.indexOf('/');
        if (firstSlash < 0) {
            return false;
        }
        String pathAfterRoot = entryName.substring(firstSlash + 1);

        String modulesPrefix;
        if (startPath == null || startPath.isEmpty()) {
            modulesPrefix = "modules/";
        } else {
            modulesPrefix = startPath + "/modules/";
        }

        if (!pathAfterRoot.startsWith(modulesPrefix)) {
            return false;
        }

        String afterModules = pathAfterRoot.substring(modulesPrefix.length());
        return !afterModules.startsWith("ROOT/");
    }

    private String buildAntoraYmlPath(String startPath) {
        if (startPath == null || startPath.isEmpty()) {
            return "antora.yml";
        }
        String normalizedPath = startPath.endsWith("/") ? startPath : startPath + "/";
        return normalizedPath + "antora.yml";
    }

    private void extractTitle(ZipInputStream zis, Path outputDir) {
        try {
            byte[] content = zis.readAllBytes();
            AntoraComponentDescriptor descriptor = YAML_MAPPER.readValue(content, AntoraComponentDescriptor.class);
            if (descriptor.title != null && !descriptor.title.isBlank()) {
                Files.writeString(outputDir.resolve(".extension-title"), descriptor.title);
            }
        } catch (Exception e) {
            log.warn("Failed to parse antora.yml for title extraction: {}", e.getMessage());
        }
    }
}
