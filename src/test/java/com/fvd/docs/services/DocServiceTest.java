package com.fvd.docs.services;

import com.fvd.cache.services.CacheService;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.clients.GithubApiFile;
import com.fvd.github.services.GitHubService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    GitHubService gitHubService;

    DocService docService;
    DocStore docStore;

    @BeforeEach
    void setUp() {
        CacheService cacheService = new CacheService(tempDir.toString());
        docStore = new DocStore(cacheService);
        docService = new DocService(docStore, gitHubService);
    }

    @Test
    void getOrFetchDocReturnsCachedContentWithoutCallingGitHub() {
        docStore.write("3.27", "security-oidc.adoc", "= Security OIDC");

        String result = docService.getOrFetchDoc("3.27", "security-oidc.adoc");

        assertThat(result).isEqualTo("= Security OIDC");
        verify(gitHubService, never()).fetchFileContent("security-oidc.adoc", "3.27");
    }

    @Test
    void getOrFetchDocFetchesFromGitHubOnCacheMiss() {
        String content = "= Fetched Doc\nSome content";
        String encoded = Base64.getEncoder().encodeToString(content.getBytes());
        GithubApiFile file = new GithubApiFile("security-oidc.adoc",
                "docs/src/main/asciidoc/security-oidc.adoc", "abc123", encoded, "base64");
        when(gitHubService.fetchFileContent("security-oidc.adoc", "3.27")).thenReturn(file);

        String result = docService.getOrFetchDoc("3.27", "security-oidc.adoc");

        assertThat(result).isEqualTo(content);
        verify(gitHubService).fetchFileContent("security-oidc.adoc", "3.27");
        // Also verify it was cached for next time
        assertThat(docStore.read("3.27", "security-oidc.adoc")).isPresent().hasValue(content);
    }

    @Test
    void getOrFetchDocHandlesBase64Content() {
        String original = "= Hello World\nSome content";
        String encoded = Base64.getEncoder().encodeToString(original.getBytes());
        GithubApiFile file = new GithubApiFile("test.adoc", "path/test.adoc", "sha1", encoded, "base64");
        when(gitHubService.fetchFileContent("test.adoc", "3.27")).thenReturn(file);

        String result = docService.getOrFetchDoc("3.27", "test.adoc");

        assertThat(result).isEqualTo(original);
    }

    @Test
    void getOrFetchDocHandlesBase64WithNewlines() {
        String original = "= Hello World\nSome content that is long enough to have line breaks in base64";
        String encoded = Base64.getEncoder().encodeToString(original.getBytes());
        String withNewlines = encoded.replaceAll("(.{76})", "$1\n");
        GithubApiFile file = new GithubApiFile("test.adoc", "path/test.adoc", "sha1", withNewlines, "base64");
        when(gitHubService.fetchFileContent("test.adoc", "3.27")).thenReturn(file);

        String result = docService.getOrFetchDoc("3.27", "test.adoc");

        assertThat(result).isEqualTo(original);
    }

    @Test
    void getOrFetchDocHandlesPlainText() {
        GithubApiFile file = new GithubApiFile("test.adoc", "path/test.adoc", "sha1", "plain text content", null);
        when(gitHubService.fetchFileContent("test.adoc", "3.27")).thenReturn(file);

        String result = docService.getOrFetchDoc("3.27", "test.adoc");

        assertThat(result).isEqualTo("plain text content");
    }
}
