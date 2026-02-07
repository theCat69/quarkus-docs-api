package com.fvd.indexs.stores;

import com.fvd.cache.services.CacheService;
import com.fvd.common.validators.InputValidator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

@ApplicationScoped
public class KeywordIndexStore {

    private static final String KEYWORD_INDEX_NAME = "keyword_index.json";

    private final CacheService cacheService;

    @Inject
    public KeywordIndexStore(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    public Optional<String> read(String version) {
        InputValidator.validateVersion(version);
        Path indexFile = cacheService.versionDir(version).resolve(KEYWORD_INDEX_NAME);
        if (!Files.exists(indexFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(indexFile));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read keyword index for version: " + version, e);
        }
    }

    public void write(String version, String json) {
        InputValidator.validateVersion(version);
        cacheService.ensureVersionDir(version);
        Path indexFile = cacheService.versionDir(version).resolve(KEYWORD_INDEX_NAME);
        atomicWrite(indexFile, json);
    }

    private void atomicWrite(Path target, String content) {
        try {
            Path temp = Files.createTempFile(target.getParent(), "kwidx", ".tmp");
            Files.writeString(temp, content);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write keyword index: " + target, e);
        }
    }
}
