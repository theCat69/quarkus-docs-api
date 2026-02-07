package com.fvd.indexs.services;

import com.fvd.cache.services.CacheService;
import com.fvd.github.clients.GitHubService;
import com.fvd.indexs.stores.IndexStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    GitHubService gitHubService;

    IndexService indexService;
    IndexStore indexStore;

    @BeforeEach
    void setUp() {
        CacheService cacheService = new CacheService(tempDir.toString());
        indexStore = new IndexStore(cacheService);
        indexService = new IndexService(indexStore, gitHubService);
    }

    @Test
    void getOrFetchIndexReturnsCachedIndexWithoutCallingGitHub() {
        String json = "[{\"name\":\"test.adoc\",\"sha\":\"abc123\"}]";
        indexStore.writeRaw("3.21", json);

        String result = indexService.getOrFetchIndex("3.21");

        assertThat(result).isEqualTo(json);
        verify(gitHubService, never()).fetchIndex("3.21");
    }

    @Test
    void getOrFetchIndexFetchesFromGitHubOnCacheMiss() {
        String json = "[{\"name\":\"fetched.adoc\",\"sha\":\"def456\"}]";
        when(gitHubService.fetchIndex("3.21")).thenReturn(json);

        String result = indexService.getOrFetchIndex("3.21");

        assertThat(result).isEqualTo(json);
        verify(gitHubService).fetchIndex("3.21");
        // Also verify it was cached for next time
        assertThat(indexStore.readRaw("3.21")).isPresent().hasValue(json);
    }
}
