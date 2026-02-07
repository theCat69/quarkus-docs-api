package com.fvd.docs.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fvd.cache.services.CacheService;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.clients.GitHubService;
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
        docService = new DocService(docStore, gitHubService, new ObjectMapper());
    }

    @Test
    void getOrFetchDocReturnsCachedContentWithoutCallingGitHub() {
        docStore.write("3.21", "security-oidc.adoc", "= Security OIDC");

        String result = docService.getOrFetchDoc("3.21", "security-oidc.adoc");

        assertThat(result).isEqualTo("= Security OIDC");
        verify(gitHubService, never()).fetchFileContent("security-oidc.adoc", "3.21");
    }

    @Test
    void getOrFetchDocFetchesFromGitHubOnCacheMiss() {
        String content = "= Fetched Doc\nSome content";
        String encoded = Base64.getEncoder().encodeToString(content.getBytes());
        String jsonResponse = "{\"encoding\":\"base64\",\"content\":\"" + encoded + "\"}";
        when(gitHubService.fetchFileContent("security-oidc.adoc", "3.21")).thenReturn(jsonResponse);

        String result = docService.getOrFetchDoc("3.21", "security-oidc.adoc");

        assertThat(result).isEqualTo(content);
        verify(gitHubService).fetchFileContent("security-oidc.adoc", "3.21");
        // Also verify it was cached for next time
        assertThat(docStore.read("3.21", "security-oidc.adoc")).isPresent().hasValue(content);
    }

    @Test
    void decodeContentHandlesBase64() {
        String original = "= Hello World\nSome content";
        String encoded = Base64.getEncoder().encodeToString(original.getBytes());
        String json = "{\"encoding\":\"base64\",\"content\":\"" + encoded + "\"}";

        String result = docService.decodeContent(json, "test.adoc");

        assertThat(result).isEqualTo(original);
    }

    @Test
    void decodeContentHandlesBase64WithNewlines() {
        String original = "= Hello World\nSome content that is long enough to have line breaks in base64";
        String encoded = Base64.getEncoder().encodeToString(original.getBytes());
        String withNewlines = encoded.replaceAll("(.{76})", "$1\\\\n");
        String json = "{\"encoding\":\"base64\",\"content\":\"" + withNewlines + "\"}";

        String result = docService.decodeContent(json, "test.adoc");

        assertThat(result).isEqualTo(original);
    }

    @Test
    void decodeContentHandlesPlainText() {
        String json = "{\"content\":\"plain text content\"}";

        String result = docService.decodeContent(json, "test.adoc");

        assertThat(result).isEqualTo("plain text content");
    }
}
