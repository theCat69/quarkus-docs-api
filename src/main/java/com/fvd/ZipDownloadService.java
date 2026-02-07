package com.fvd;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ZipDownloadService {

    private static final String ASCIIDOC_PREFIX = "docs/src/main/asciidoc/";

    private final GitHubClient gitHubClient;
    private final DocStore docStore;

    @Inject
    public ZipDownloadService(GitHubClient gitHubClient, DocStore docStore) {
        this.gitHubClient = gitHubClient;
        this.docStore = docStore;
    }

    public void extractDocsSubfolder(String version) {
        InputValidator.validateVersion(version);
        try (InputStream zipStream = gitHubClient.fetchZipStream(version);
             ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName();
                String relativePath = extractRelativePath(entryName);
                if (relativePath != null) {
                    String content = new String(zis.readAllBytes());
                    docStore.write(version, relativePath, content);
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new UpstreamException("Failed to extract zip for version: " + version, e);
        }
    }

    String extractRelativePath(String entryName) {
        // Zip entries start with "quarkus-<version>/docs/src/main/asciidoc/..."
        // We need to strip the repo prefix and the asciidoc prefix
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
