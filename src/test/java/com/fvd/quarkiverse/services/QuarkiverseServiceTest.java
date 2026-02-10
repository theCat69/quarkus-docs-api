package com.fvd.quarkiverse.services;

import com.fvd.cache.services.CacheService;
import com.fvd.github.clients.GithubApiFile;
import com.fvd.github.clients.GithubApiIndex;
import com.fvd.github.services.GitHubService;
import com.fvd.indexs.stores.IndexStore;
import com.fvd.quarkiverse.parser.AntoraPlaybookParser;
import com.fvd.quarkiverse.parser.ResolvedContentSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuarkiverseServiceTest {

    @Mock
    private GitHubService gitHubService;

    @Mock
    private IndexStore indexStore;

    private CacheService cacheService;
    private AntoraPlaybookParser parser;
    private QuarkiverseZipExtractor zipExtractor;
    private QuarkiverseService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        cacheService = new CacheService(tempDir.toString());
        cacheService.ensureVersionDir("main");
        parser = new AntoraPlaybookParser();
        zipExtractor = new QuarkiverseZipExtractor();
        service = new QuarkiverseService(
                gitHubService, cacheService, indexStore,
                parser, zipExtractor,
                "quarkiverse/quarkiverse-docs", "main", 4
        );
    }

    @Test
    void fetchAndExtractAllFetchesPlaybookAndExtractsDocs() throws Exception {
        String playbookYaml = """
                content:
                  sources:
                    - url: https://github.com/quarkiverse/quarkus-openapi-generator
                      branches: main
                      start_path: docs
                """;

        GithubApiFile playbookFile = new GithubApiFile();
        playbookFile.content = java.util.Base64.getEncoder().encodeToString(playbookYaml.getBytes());
        playbookFile.encoding = "base64";

        when(gitHubService.fetchFileContentForRepo(
                "quarkiverse", "quarkiverse-docs", "antora-playbook.yml", "main"))
                .thenReturn(playbookFile);

        byte[] zipBytes = createZip(
                "quarkus-openapi-generator-main/docs/modules/ROOT/pages/index.adoc", "= OpenAPI Generator"
        );
        when(gitHubService.fetchZipStreamForRepo("quarkiverse", "quarkus-openapi-generator", "main"))
                .thenReturn(new ByteArrayInputStream(zipBytes));

        List<String> result = service.fetchAndExtractAll();

        assertThat(result).containsExactly("quarkiverse/quarkus-openapi-generator/index.adoc");
    }

    @Test
    void fetchAndExtractAllContinuesWhenSingleExtensionFails() throws Exception {
        String playbookYaml = """
                content:
                  sources:
                    - url: https://github.com/quarkiverse/quarkus-failing-ext
                      branches: main
                      start_path: docs
                    - url: https://github.com/quarkiverse/quarkus-good-ext
                      branches: main
                      start_path: docs
                """;

        GithubApiFile playbookFile = new GithubApiFile();
        playbookFile.content = java.util.Base64.getEncoder().encodeToString(playbookYaml.getBytes());
        playbookFile.encoding = "base64";

        when(gitHubService.fetchFileContentForRepo(
                "quarkiverse", "quarkiverse-docs", "antora-playbook.yml", "main"))
                .thenReturn(playbookFile);

        // First extension fails
        when(gitHubService.fetchZipStreamForRepo("quarkiverse", "quarkus-failing-ext", "main"))
                .thenThrow(new RuntimeException("Download failed"));

        // Second extension succeeds
        byte[] zipBytes = createZip(
                "quarkus-good-ext-main/docs/modules/ROOT/pages/index.adoc", "= Good Extension"
        );
        when(gitHubService.fetchZipStreamForRepo("quarkiverse", "quarkus-good-ext", "main"))
                .thenReturn(new ByteArrayInputStream(zipBytes));

        List<String> result = service.fetchAndExtractAll();

        assertThat(result).containsExactly("quarkiverse/quarkus-good-ext/index.adoc");
    }

    @Test
    void fetchAndExtractAllReturnsEmptyWhenPlaybookFetchFails() {
        when(gitHubService.fetchFileContentForRepo(
                "quarkiverse", "quarkiverse-docs", "antora-playbook.yml", "main"))
                .thenThrow(new RuntimeException("Not found"));

        List<String> result = service.fetchAndExtractAll();

        assertThat(result).isEmpty();
    }

    @Test
    void refreshAllReturnsTrueWhenChangesDetected() throws Exception {
        String playbookYaml = """
                content:
                  sources:
                    - url: https://github.com/quarkiverse/quarkus-openapi-generator
                      branches: main
                      start_path: docs
                """;

        GithubApiFile playbookFile = new GithubApiFile();
        playbookFile.content = java.util.Base64.getEncoder().encodeToString(playbookYaml.getBytes());
        playbookFile.encoding = "base64";

        when(gitHubService.fetchFileContentForRepo(
                "quarkiverse", "quarkiverse-docs", "antora-playbook.yml", "main"))
                .thenReturn(playbookFile);

        // New index from GitHub
        List<GithubApiIndex> newIndex = List.of(
                new GithubApiIndex("index.adoc", "docs/modules/ROOT/pages/index.adoc", "new-sha")
        );
        when(gitHubService.fetchIndexForRepo(
                "quarkiverse", "quarkus-openapi-generator", "docs/modules/ROOT/pages", "main"))
                .thenReturn(newIndex);

        // Old stored index with different SHA
        List<GithubApiIndex> oldIndex = List.of(
                new GithubApiIndex("index.adoc", "docs/modules/ROOT/pages/index.adoc", "old-sha")
        );
        when(indexStore.read("quarkiverse/quarkus-openapi-generator"))
                .thenReturn(Optional.of(oldIndex));

        // Provide file content for the changed file
        GithubApiFile changedFile = new GithubApiFile();
        changedFile.content = java.util.Base64.getEncoder().encodeToString("= Updated Content".getBytes());
        changedFile.encoding = "base64";
        when(gitHubService.fetchFileContentForRepo(
                "quarkiverse", "quarkus-openapi-generator",
                "docs/modules/ROOT/pages/index.adoc", "main"))
                .thenReturn(changedFile);

        boolean result = service.refreshAll();

        assertThat(result).isTrue();
        verify(indexStore).write(eq("quarkiverse/quarkus-openapi-generator"), eq(newIndex));
    }

    @Test
    void refreshAllReturnsFalseWhenNoChanges() throws Exception {
        String playbookYaml = """
                content:
                  sources:
                    - url: https://github.com/quarkiverse/quarkus-openapi-generator
                      branches: main
                      start_path: docs
                """;

        GithubApiFile playbookFile = new GithubApiFile();
        playbookFile.content = java.util.Base64.getEncoder().encodeToString(playbookYaml.getBytes());
        playbookFile.encoding = "base64";

        when(gitHubService.fetchFileContentForRepo(
                "quarkiverse", "quarkiverse-docs", "antora-playbook.yml", "main"))
                .thenReturn(playbookFile);

        // Same SHA in both old and new
        List<GithubApiIndex> index = List.of(
                new GithubApiIndex("index.adoc", "docs/modules/ROOT/pages/index.adoc", "same-sha")
        );
        when(gitHubService.fetchIndexForRepo(
                "quarkiverse", "quarkus-openapi-generator", "docs/modules/ROOT/pages", "main"))
                .thenReturn(index);

        when(indexStore.read("quarkiverse/quarkus-openapi-generator"))
                .thenReturn(Optional.of(index));

        boolean result = service.refreshAll();

        assertThat(result).isFalse();
        // Should still update the stored index
        verify(indexStore).write(eq("quarkiverse/quarkus-openapi-generator"), eq(index));
    }

    @Test
    void refreshAllReturnsTrueForNewExtension() throws Exception {
        String playbookYaml = """
                content:
                  sources:
                    - url: https://github.com/quarkiverse/quarkus-new-ext
                      branches: main
                      start_path: docs
                """;

        GithubApiFile playbookFile = new GithubApiFile();
        playbookFile.content = java.util.Base64.getEncoder().encodeToString(playbookYaml.getBytes());
        playbookFile.encoding = "base64";

        when(gitHubService.fetchFileContentForRepo(
                "quarkiverse", "quarkiverse-docs", "antora-playbook.yml", "main"))
                .thenReturn(playbookFile);

        List<GithubApiIndex> newIndex = List.of(
                new GithubApiIndex("index.adoc", "docs/modules/ROOT/pages/index.adoc", "abc123")
        );
        when(gitHubService.fetchIndexForRepo(
                "quarkiverse", "quarkus-new-ext", "docs/modules/ROOT/pages", "main"))
                .thenReturn(newIndex);

        // No old index exists
        when(indexStore.read("quarkiverse/quarkus-new-ext"))
                .thenReturn(Optional.empty());

        // Provide file content
        GithubApiFile file = new GithubApiFile();
        file.content = java.util.Base64.getEncoder().encodeToString("= New Extension".getBytes());
        file.encoding = "base64";
        when(gitHubService.fetchFileContentForRepo(
                "quarkiverse", "quarkus-new-ext",
                "docs/modules/ROOT/pages/index.adoc", "main"))
                .thenReturn(file);

        boolean result = service.refreshAll();

        assertThat(result).isTrue();
    }

    private byte[] createZip(String... nameContentPairs) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < nameContentPairs.length; i += 2) {
                zos.putNextEntry(new ZipEntry(nameContentPairs[i]));
                zos.write(nameContentPairs[i + 1].getBytes());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
