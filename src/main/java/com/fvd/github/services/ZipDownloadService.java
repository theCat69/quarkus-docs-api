package com.fvd.github.services;

import com.fvd.cache.services.CacheService;
import com.fvd.common.validators.InputValidator;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.clients.GitHubService;
import com.fvd.github.exceptions.UpstreamException;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ZipDownloadService {

    private static final String ASCIIDOC_PREFIX = "docs/src/main/asciidoc/";

    private final GitHubService gitHubService;
    private final DocStore docStore;
    private final CacheService cacheService;

    /**
     * Streams the zip archive for the given version, extracts asciidoc files
     * into a staging directory, then moves them to the real cache on success.
     *
     * @return list of extracted file names (relative paths within the asciidoc subtree)
     */
    public List<String> streamAndExtract(String version) {
        InputValidator.validateVersion(version);
        cacheService.ensureVersionDir(version);
        Path stagingDir = cacheService.versionDir(version).resolve("docs-staging");
        List<String> extractedFiles = new ArrayList<>();

        try {
            Files.createDirectories(stagingDir);
            extractToStaging(version, stagingDir, extractedFiles);
            moveStagingToCache(version, stagingDir, extractedFiles);
            log.info("Extracted {} asciidoc files for version {}", extractedFiles.size(), version);
        } catch (IOException e) {
            cleanupStagingDir(stagingDir);
            throw new UpstreamException("Failed to extract zip for version: " + version, e);
        } catch (UpstreamException e) {
            cleanupStagingDir(stagingDir);
            throw e;
        }

        return extractedFiles;
    }

    private void extractToStaging(String version, Path stagingDir, List<String> extractedFiles)
            throws IOException {
        try (InputStream zipStream = gitHubService.fetchZipStream(version);
             ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName();
                String relativePath = extractRelativePath(entryName);
                if (relativePath != null) {
                    Path targetFile = stagingDir.resolve(relativePath);
                    Files.createDirectories(targetFile.getParent());
                    Files.writeString(targetFile, new String(zis.readAllBytes()));
                    extractedFiles.add(relativePath);
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
        cleanupStagingDir(stagingDir);
    }

    private void cleanupStagingDir(Path stagingDir) {
        if (!Files.exists(stagingDir)) {
            return;
        }
        try {
            Files.walkFileTree(stagingDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Failed to clean up staging directory: {}", stagingDir, e);
        }
    }

    /**
     * Legacy method kept for backward compatibility.
     * Prefer {@link #streamAndExtract(String)} for safe extraction.
     */
    public void extractDocsSubfolder(String version) {
        streamAndExtract(version);
    }

    String extractRelativePath(String entryName) {
        int asciidocIdx = entryName.indexOf(ASCIIDOC_PREFIX);
        if (asciidocIdx < 0) {
            return null;
        }
        String relativePath = entryName.substring(asciidocIdx + ASCIIDOC_PREFIX.length());
        if (relativePath.isEmpty()) {
            return null;
        }
        return relativePath;
    }
}
