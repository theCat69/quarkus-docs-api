package com.fvd.github.services;

import com.fvd.cache.services.CacheService;
import com.fvd.common.utils.FileUtils;
import com.fvd.common.validators.InputValidator;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.exceptions.UpstreamException;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ZipDownloadService {

    private final GitHubService gitHubService;
    private final DocStore docStore;
    private final CacheService cacheService;
    private final DocParser docParser;

    /**
     * Downloads the zip archive once and extracts asciidoc files for all specified versions.
     * Matches entries against {@code _versions/<V>/guides/*.adoc} for each V in the versions list.
     *
     * @return map of version to list of extracted file names (relative paths within each version's docs)
     */
    public Map<String, List<String>> streamAndExtractAll(List<String> versions) {
        Map<String, List<String>> result = new HashMap<>();
        Map<String, Path> stagingDirs = new HashMap<>();

        try {
            // Prepare staging directories for each version
            for (String version : versions) {
                InputValidator.validateVersion(version);
                cacheService.ensureVersionDir(version);
                Path stagingDir = cacheService.versionDir(version).resolve("docs-staging");
                Files.createDirectories(stagingDir);
                stagingDirs.put(version, stagingDir);
                result.put(version, new ArrayList<>());
            }

            // Download zip once and extract for all versions
            extractToStagingAll(versions, stagingDirs, result);

            // Move staging to cache for each version
            for (String version : versions) {
                Path stagingDir = stagingDirs.get(version);
                List<String> extractedFiles = result.get(version);
                moveStagingToCache(version, stagingDir, extractedFiles);
                log.info("Extracted {} asciidoc files for version {}", extractedFiles.size(), version);
            }
        } catch (IOException e) {
            stagingDirs.values().forEach(FileUtils::deleteDirectoryQuietly);
            throw new UpstreamException("Failed to extract zip for versions: " + versions, e);
        } catch (UpstreamException e) {
            stagingDirs.values().forEach(FileUtils::deleteDirectoryQuietly);
            throw e;
        }

        return result;
    }

    /**
     * Streams the zip archive for the given version, extracts asciidoc files
     * into a staging directory, then moves them to the real cache on success.
     * Delegates to {@link #streamAndExtractAll(List)}.
     *
     * @return list of extracted file names (relative paths within the asciidoc subtree)
     */
    public List<String> streamAndExtract(String version) {
        Map<String, List<String>> result = streamAndExtractAll(List.of(version));
        return result.getOrDefault(version, List.of());
    }

    private void extractToStagingAll(List<String> versions, Map<String, Path> stagingDirs,
                                     Map<String, List<String>> result) throws IOException {
        try (InputStream zipStream = gitHubService.fetchZipStream();
             ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().endsWith(docParser.fileSuffix())) {
                    zis.closeEntry();
                    continue;
                }
                String entryName = entry.getName();
                byte[] content = zis.readAllBytes();

                for (String version : versions) {
                    String relativePath = extractRelativePath(entryName, version);
                    if (relativePath != null) {
                        Path stagingDir = stagingDirs.get(version);
                        Path targetFile = stagingDir.resolve(relativePath);
                        Files.createDirectories(targetFile.getParent());
                        Files.writeString(targetFile, new String(content));
                        result.get(version).add(relativePath);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void moveStagingToCache(String version, Path stagingDir, List<String> extractedFiles)
            throws IOException {
        for (String relativePath : extractedFiles) {
            Path staged = stagingDir.resolve(relativePath);
            String content = Files.readString(staged);
            docStore.write(version, relativePath, content);
        }
        FileUtils.deleteDirectoryQuietly(stagingDir);
    }

    /**
     * Legacy method kept for backward compatibility.
     * Prefer {@link #streamAndExtract(String)} for safe extraction.
     */
    public void extractDocsSubfolder(String version) {
        streamAndExtract(version);
    }

    String extractRelativePath(String entryName, String version) {
        String prefix = docParser.docsPrefix(version);
        int prefixIdx = entryName.indexOf(prefix);
        if (prefixIdx < 0) {
            return null;
        }
        String relativePath = entryName.substring(prefixIdx + prefix.length());
        if (relativePath.isEmpty()) {
            return null;
        }
        return relativePath;
    }
}
