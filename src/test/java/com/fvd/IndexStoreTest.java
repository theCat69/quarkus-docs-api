package com.fvd;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexStoreTest {

    @TempDir
    Path tempDir;

    IndexStore indexStore;

    @BeforeEach
    void setUp() {
        CacheService cacheService = new CacheService(tempDir.toString());
        indexStore = new IndexStore(cacheService);
    }

    @Test
    void readRawReturnsEmptyWhenMissing() {
        Optional<String> result = indexStore.readRaw("3.21");
        assertThat(result).isEmpty();
    }

    @Test
    void writeAndReadRoundTrip() {
        String json = "[{\"name\":\"test.adoc\"}]";
        indexStore.writeRaw("3.21", json);
        Optional<String> result = indexStore.readRaw("3.21");
        assertThat(result).isPresent().contains(json);
    }

    @Test
    void writeOverwritesExisting() {
        indexStore.writeRaw("3.21", "[\"old\"]");
        indexStore.writeRaw("3.21", "[\"new\"]");
        assertThat(indexStore.readRaw("3.21")).contains("[\"new\"]");
    }

    @Test
    void readRawRejectsInvalidVersion() {
        assertThatThrownBy(() -> indexStore.readRaw("../etc"))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void writeRawRejectsInvalidVersion() {
        assertThatThrownBy(() -> indexStore.writeRaw("../etc", "[]"))
                .isInstanceOf(InvalidInputException.class);
    }
}
