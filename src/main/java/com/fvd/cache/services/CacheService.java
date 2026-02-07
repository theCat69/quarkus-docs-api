package com.fvd.cache.services;

import com.fvd.common.validators.InputValidator;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@ApplicationScoped
public class CacheService {

    private final Path cacheRoot;

    public CacheService(@ConfigProperty(name = "app.cache.dir", defaultValue = ".cache") String cacheDir) {
        this.cacheRoot = Path.of(cacheDir);
    }

    public Path getCacheRoot() {
        return cacheRoot;
    }

    public Path versionDir(String version) {
        InputValidator.validateVersion(version);
        return cacheRoot.resolve(version);
    }

    public Path ensureVersionDir(String version) {
        Path dir = versionDir(version);
        try {
            Files.createDirectories(dir.resolve("docs"));
            return dir;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create cache directory for version: " + version, e);
        }
    }

    public boolean versionExists(String version) {
        return Files.isDirectory(versionDir(version));
    }

    public List<String> listCachedVersions() {
        if (!Files.isDirectory(cacheRoot)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(cacheRoot)) {
            return dirs
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
