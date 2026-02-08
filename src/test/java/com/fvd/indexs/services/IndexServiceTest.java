package com.fvd.indexs.services;

import com.fvd.github.clients.GithubApiIndex;
import com.fvd.github.services.GitHubService;
import com.fvd.indexs.stores.IndexStore;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;

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
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db"));
        SqliteSchemaInitializer initializer = new SqliteSchemaInitializer(ds);
        initializer.initSchema();
        indexStore = new IndexStore(ds);
        indexService = new IndexService(indexStore, gitHubService);
    }

    @Test
    void getOrFetchIndexReturnsCachedIndexWithoutCallingGitHub() {
        List<GithubApiIndex> index = List.of(new GithubApiIndex("test.adoc", "path/test.adoc", "abc123"));
        indexStore.write("3.27", index);

        List<GithubApiIndex> result = indexService.getOrFetchIndex("3.27");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name).isEqualTo("test.adoc");
        assertThat(result.get(0).sha).isEqualTo("abc123");
        verify(gitHubService, never()).fetchIndex("3.27");
    }

    @Test
    void getOrFetchIndexFetchesFromGitHubOnCacheMiss() {
        List<GithubApiIndex> index = List.of(new GithubApiIndex("fetched.adoc", "path/fetched.adoc", "def456"));
        when(gitHubService.fetchIndex("3.27")).thenReturn(index);

        List<GithubApiIndex> result = indexService.getOrFetchIndex("3.27");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name).isEqualTo("fetched.adoc");
        assertThat(result.get(0).sha).isEqualTo("def456");
        verify(gitHubService).fetchIndex("3.27");
        // Also verify it was cached for next time
        assertThat(indexStore.read("3.27")).isPresent();
    }
}
