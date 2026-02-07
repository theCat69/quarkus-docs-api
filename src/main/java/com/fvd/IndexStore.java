package com.fvd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class IndexStore {

    private static final String FILE_INDEX_NAME = "file_index.json";

    private final CacheService cacheService;

    @Inject
    public IndexStore(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    public Optional<String> readRaw(String version) {
        InputValidator.validateVersion(version);
        Path indexFile = cacheService.versionDir(version).resolve(FILE_INDEX_NAME);
        if (!Files.exists(indexFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(indexFile));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file index for version: " + version, e);
        }
    }

    public void writeRaw(String version, String json) {
        InputValidator.validateVersion(version);
        cacheService.ensureVersionDir(version);
        Path indexFile = cacheService.versionDir(version).resolve(FILE_INDEX_NAME);
        atomicWrite(indexFile, json);
    }

    private void atomicWrite(Path target, String content) {
        try {
            Path temp = Files.createTempFile(target.getParent(), "idx", ".tmp");
            Files.writeString(temp, content);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file index: " + target, e);
        }
    }
}
