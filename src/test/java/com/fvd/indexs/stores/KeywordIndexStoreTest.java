package com.fvd.indexs.stores;

import com.fvd.cache.services.CacheService;
import com.fvd.common.exceptions.InvalidInputException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeywordIndexStoreTest {

    @TempDir
    Path tempDir;

    KeywordIndexStore keywordIndexStore;

    @BeforeEach
    void setUp() {
        CacheService cacheService = new CacheService(tempDir.toString());
        keywordIndexStore = new KeywordIndexStore(cacheService);
    }

    @Test
    void readReturnsEmptyWhenMissing() {
        Optional<String> result = keywordIndexStore.read("3.21");
        assertThat(result).isEmpty();
    }

    @Test
    void writeAndReadRoundTrip() {
        String json = "{\"files\":[]}";
        keywordIndexStore.write("3.21", json);
        Optional<String> result = keywordIndexStore.read("3.21");
        assertThat(result).isPresent().contains(json);
    }

    @Test
    void writeOverwritesExisting() {
        keywordIndexStore.write("3.21", "{\"old\":true}");
        keywordIndexStore.write("3.21", "{\"new\":true}");
        assertThat(keywordIndexStore.read("3.21")).contains("{\"new\":true}");
    }

    @Test
    void readRejectsInvalidVersion() {
        assertThatThrownBy(() -> keywordIndexStore.read("../etc"))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void writeRejectsInvalidVersion() {
        assertThatThrownBy(() -> keywordIndexStore.write("../etc", "{}"))
                .isInstanceOf(InvalidInputException.class);
    }
}
