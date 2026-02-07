package com.fvd;

import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class DocServiceTest {

    @TempDir
    Path tempDir;

    DocService docService;
    DocStore docStore;

    @BeforeEach
    void setUp() {
        CacheService cacheService = new CacheService(tempDir.toString());
        docStore = new DocStore(cacheService);
        GitHubClient gitHubClient = new GitHubClient(Optional.empty(),
                "https://api.github.com/repos/quarkusio/quarkus/contents/",
                "https://github.com/quarkusio/quarkus/archive/refs/heads/");
        docService = new DocService(docStore, gitHubClient, new ObjectMapper());
    }

    @Test
    void getOrFetchDocReturnsCachedContent() {
        docStore.write("3.21", "security-oidc.adoc", "= Security OIDC");
        String result = docService.getOrFetchDoc("3.21", "security-oidc.adoc");
        assertThat(result).isEqualTo("= Security OIDC");
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
        // Simulate GitHub's line-broken base64 by adding \n characters within the content
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
