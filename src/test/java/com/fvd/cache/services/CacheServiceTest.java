package com.fvd.cache.services;

import com.fvd.common.exceptions.InvalidInputException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheServiceTest {

    @TempDir
    Path tempDir;

    CacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new CacheService(tempDir.toString());
    }

    @Test
    void ensureVersionDirCreatesDirectoryStructure() {
        Path dir = cacheService.ensureVersionDir("3.27");
        assertThat(dir).isDirectory();
        assertThat(dir.resolve("docs")).isDirectory();
    }

    @Test
    void versionDirReturnsCorrectPath() {
        Path dir = cacheService.versionDir("3.27");
        assertThat(dir).isEqualTo(tempDir.resolve("3.27"));
    }

    @Test
    void versionExistsReturnsFalseWhenMissing() {
        assertThat(cacheService.versionExists("3.27")).isFalse();
    }

    @Test
    void versionExistsReturnsTrueWhenPresent() {
        cacheService.ensureVersionDir("3.27");
        assertThat(cacheService.versionExists("3.27")).isTrue();
    }

    @Test
    void listCachedVersionsReturnsEmptyWhenNone() {
        assertThat(cacheService.listCachedVersions()).isEmpty();
    }

    @Test
    void listCachedVersionsReturnsCreatedVersions() {
        cacheService.ensureVersionDir("3.27");
        cacheService.ensureVersionDir("3.22");
        List<String> versions = cacheService.listCachedVersions();
        assertThat(versions).containsExactlyInAnyOrder("3.27", "3.22");
    }

    @Test
    void versionDirRejectsInvalidVersion() {
        assertThatThrownBy(() -> cacheService.versionDir("../etc"))
                .isInstanceOf(InvalidInputException.class);
    }
}
