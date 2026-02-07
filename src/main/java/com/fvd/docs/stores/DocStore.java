package com.fvd.docs.stores;

import com.fvd.cache.services.CacheService;
import com.fvd.common.validators.InputValidator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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

    public List<String> listDocFiles(String version) {
        Path docsDir = cacheService.versionDir(version).resolve("docs");
        if (!Files.isDirectory(docsDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(docsDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(docsDir::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to list doc files for version: " + version, e);
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
