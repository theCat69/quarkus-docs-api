package com.fvd.indexs.stores;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fvd.cache.services.CacheService;
import com.fvd.common.validators.InputValidator;
import com.fvd.github.clients.GithubApiIndex;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor
public class IndexStore {

    private static final String FILE_INDEX_NAME = "file_index.json";

    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    public Optional<List<GithubApiIndex>> read(String version) {
        InputValidator.validateVersion(version);
        Path indexFile = cacheService.versionDir(version).resolve(FILE_INDEX_NAME);
        if (!Files.exists(indexFile)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(indexFile);
            List<GithubApiIndex> index = objectMapper.readValue(json, new TypeReference<>() {});
            return Optional.of(index);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file index for version: " + version, e);
        }
    }

    public void write(String version, List<GithubApiIndex> index) {
        InputValidator.validateVersion(version);
        cacheService.ensureVersionDir(version);
        Path indexFile = cacheService.versionDir(version).resolve(FILE_INDEX_NAME);
        try {
            String json = objectMapper.writeValueAsString(index);
            atomicWrite(indexFile, json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize file index for version: " + version, e);
        }
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
