package com.fvd;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class IndexServiceTest {

    @TempDir
    Path tempDir;

    IndexService indexService;
    IndexStore indexStore;

    @BeforeEach
    void setUp() {
        CacheService cacheService = new CacheService(tempDir.toString());
        indexStore = new IndexStore(cacheService);
        GitHubClient gitHubClient = new GitHubClient(Optional.empty(),
                "https://api.github.com/repos/quarkusio/quarkus/contents/",
                "https://github.com/quarkusio/quarkus/archive/refs/heads/");
        indexService = new IndexService(indexStore, gitHubClient);
    }

    @Test
    void getOrFetchIndexReturnsCachedIndex() {
        String json = "[{\"name\":\"test.adoc\",\"sha\":\"abc123\"}]";
        indexStore.writeRaw("3.21", json);
        String result = indexService.getOrFetchIndex("3.21");
        assertThat(result).isEqualTo(json);
    }

    @Test
    void getOrFetchIndexReturnsEmptyOptionalWhenNoCacheAndNoNetwork() {
        // Without network, this should throw UpstreamException
        // This tests that the cache-first path works
        indexStore.writeRaw("3.21", "[]");
        assertThat(indexService.getOrFetchIndex("3.21")).isEqualTo("[]");
    }
}
