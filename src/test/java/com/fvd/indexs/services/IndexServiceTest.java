package com.fvd.indexs.services;

import com.fvd.github.clients.GithubApiIndex;
import com.fvd.github.services.GitHubService;
import com.fvd.indexs.stores.IndexStore;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class IndexServiceTest {

    @Inject
    IndexService indexService;

    @Inject
    IndexStore indexStore;

    @Inject
    DataSource dataSource;

    @InjectMock
    GitHubService gitHubService;

    @BeforeEach
    void cleanup() throws SQLException {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE files, file_keywords, sections, section_keywords, "
                + "code_samples, code_sample_keywords, github_index, document_metadata CASCADE");
        }
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
