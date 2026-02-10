package com.fvd.github.services;

import com.fvd.docs.parser.DocParser;
import com.fvd.github.clients.GithubApiClient;
import com.fvd.github.clients.GithubApiFile;
import com.fvd.github.clients.GithubApiIndex;
import com.fvd.github.clients.GithubRepositoryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubServiceTest {

    @Mock
    private GithubApiClient githubApiClient;

    @Mock
    private GithubRepositoryClient githubRepositoryClient;

    @Mock
    private DocParser docParser;

    private GitHubService service;

    @BeforeEach
    void setUp() {
        service = new GitHubService(docParser);
        service.githubApiClient = githubApiClient;
        service.githubRepositoryClient = githubRepositoryClient;
    }

    @Test
    void fetchZipStreamForRepoCallsClientWithCorrectParams() {
        InputStream expected = new ByteArrayInputStream(new byte[0]);
        when(githubRepositoryClient.fetchZipStream("quarkiverse", "quarkus-cxf", "main"))
                .thenReturn(expected);

        InputStream result = service.fetchZipStreamForRepo("quarkiverse", "quarkus-cxf", "main");

        assertThat(result).isSameAs(expected);
        verify(githubRepositoryClient).fetchZipStream("quarkiverse", "quarkus-cxf", "main");
    }

    @Test
    void fetchIndexForRepoCallsClientWithCorrectParams() {
        List<GithubApiIndex> expected = List.of(
                new GithubApiIndex("index.adoc", "docs/modules/ROOT/pages/index.adoc", "abc123")
        );
        when(githubApiClient.fetchIndex("quarkiverse", "quarkus-cxf", "docs/modules/ROOT/pages", "main"))
                .thenReturn(expected);

        List<GithubApiIndex> result = service.fetchIndexForRepo(
                "quarkiverse", "quarkus-cxf", "docs/modules/ROOT/pages", "main");

        assertThat(result).isSameAs(expected);
        verify(githubApiClient).fetchIndex("quarkiverse", "quarkus-cxf", "docs/modules/ROOT/pages", "main");
    }

    @Test
    void fetchFileContentForRepoCallsClientWithCorrectParams() {
        GithubApiFile expected = new GithubApiFile();
        expected.content = "dGVzdA=="; // "test" base64
        when(githubApiClient.fetchFile("quarkiverse", "quarkus-cxf", "antora-playbook.yml", "main"))
                .thenReturn(expected);

        GithubApiFile result = service.fetchFileContentForRepo(
                "quarkiverse", "quarkus-cxf", "antora-playbook.yml", "main");

        assertThat(result).isSameAs(expected);
        verify(githubApiClient).fetchFile("quarkiverse", "quarkus-cxf", "antora-playbook.yml", "main");
    }
}
