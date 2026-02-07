package com.fvd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DocStore {

    private final CacheService cacheService;

    @Inject
    public DocStore(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    public Optional<String> read(String version, String filePath) {
        InputValidator.validateVersion(version);
        InputValidator.validatePath(filePath);
        Path docFile = cacheService.versionDir(version).resolve("docs").resolve(filePath);
        if (!Files.exists(docFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(docFile));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read doc: " + filePath, e);
        }
    }

    public void write(String version, String filePath, String content) {
        InputValidator.validateVersion(version);
        InputValidator.validatePath(filePath);
        cacheService.ensureVersionDir(version);
        Path docFile = cacheService.versionDir(version).resolve("docs").resolve(filePath);
        try {
            Files.createDirectories(docFile.getParent());
            Files.writeString(docFile, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write doc: " + filePath, e);
        }
    }

    public boolean docsExist(String version) {
        Path docsDir = cacheService.versionDir(version).resolve("docs");
        if (!Files.isDirectory(docsDir)) {
            return false;
        }
        try (var stream = Files.list(docsDir)) {
            return stream.findAny().isPresent();
        } catch (IOException e) {
            return false;
        }
    }
}
