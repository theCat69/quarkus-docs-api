package com.fvd.cache.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fvd.cache.services.CacheService;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.clients.GitHubClient;
import com.fvd.github.exceptions.UpstreamException;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.indexs.stores.IndexStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheRefreshJobTest {

    @Mock
    private CacheService cacheService;

    @Mock
    private GitHubClient gitHubClient;

    @Mock
    private IndexStore indexStore;

    @Mock
    private DocStore docStore;

    @Mock
    private KeywordIndexer keywordIndexer;

    private ObjectMapper objectMapper;
    private CacheRefreshJob job;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        job = new CacheRefreshJob(cacheService, gitHubClient, indexStore, docStore, keywordIndexer, objectMapper);
    }

    @Test
    void refreshSkipsWhenNoCachedVersions() {
        when(cacheService.listCachedVersions()).thenReturn(List.of());

        job.refresh();

        verify(gitHubClient, never()).fetchIndex(anyString());
    }

    @Test
    void refreshFetchesNewIndexForCachedVersion() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.21"));
        when(indexStore.readRaw("3.21")).thenReturn(java.util.Optional.of(oldIndex()));
        when(gitHubClient.fetchIndex("3.21")).thenReturn(oldIndex());
        when(keywordIndexer.build(eq("3.21"), any())).thenReturn(new KeywordIndex(List.of()));

        job.refresh();

        verify(gitHubClient).fetchIndex("3.21");
    }

    @Test
    void refreshDetectsChangedFilesAndRefetches() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.21"));
        when(indexStore.readRaw("3.21")).thenReturn(java.util.Optional.of(oldIndex()));
        when(gitHubClient.fetchIndex("3.21")).thenReturn(newIndexWithChangedSha());
        String docJson = githubDocResponse("updated content");
        when(gitHubClient.fetchFileContent("security-overview.adoc", "3.21")).thenReturn(docJson);
        when(keywordIndexer.build(eq("3.21"), any())).thenReturn(new KeywordIndex(List.of()));

        job.refresh();

        // security-overview.adoc has a changed SHA, should be re-fetched and written
        verify(gitHubClient).fetchFileContent("security-overview.adoc", "3.21");
        verify(docStore).write(eq("3.21"), eq("security-overview.adoc"), eq("updated content"));
        // config.adoc has the same SHA, should NOT be re-fetched
        verify(gitHubClient, never()).fetchFileContent(eq("config.adoc"), anyString());
    }

    @Test
    void refreshReplacesFileIndexWithNewData() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.21"));
        when(indexStore.readRaw("3.21")).thenReturn(java.util.Optional.of(oldIndex()));
        String newIndex = newIndexWithChangedSha();
        when(gitHubClient.fetchIndex("3.21")).thenReturn(newIndex);
        String docJson = githubDocResponse("updated content");
        when(gitHubClient.fetchFileContent("security-overview.adoc", "3.21")).thenReturn(docJson);
        when(keywordIndexer.build(eq("3.21"), any())).thenReturn(new KeywordIndex(List.of()));

        job.refresh();

        verify(indexStore).writeRaw("3.21", newIndex);
    }

    @Test
    void refreshRebuildsKeywordIndexAfterUpdate() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.21"));
        when(indexStore.readRaw("3.21")).thenReturn(java.util.Optional.of(oldIndex()));
        when(gitHubClient.fetchIndex("3.21")).thenReturn(newIndexWithChangedSha());
        String docJson = githubDocResponse("updated content");
        when(gitHubClient.fetchFileContent("security-overview.adoc", "3.21")).thenReturn(docJson);
        when(keywordIndexer.build(eq("3.21"), any())).thenReturn(new KeywordIndex(List.of()));

        job.refresh();

        verify(keywordIndexer).build(eq("3.21"), eq(List.of("security-overview.adoc", "config.adoc")));
    }

    @Test
    void refreshHandlesNewFilesInIndex() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.21"));
        when(indexStore.readRaw("3.21")).thenReturn(java.util.Optional.of(oldIndex()));
        when(gitHubClient.fetchIndex("3.21")).thenReturn(newIndexWithAddedFile());
        String docJson = githubDocResponse("new file content");
        when(gitHubClient.fetchFileContent("new-file.adoc", "3.21")).thenReturn(docJson);
        when(keywordIndexer.build(eq("3.21"), any())).thenReturn(new KeywordIndex(List.of()));

        job.refresh();

        // new-file.adoc is not in the old index, should be fetched
        verify(gitHubClient).fetchFileContent("new-file.adoc", "3.21");
        verify(docStore).write(eq("3.21"), eq("new-file.adoc"), eq("new file content"));
    }

    @Test
    void refreshHandlesRemovedFilesInIndex() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.21"));
        when(indexStore.readRaw("3.21")).thenReturn(java.util.Optional.of(oldIndex()));
        when(gitHubClient.fetchIndex("3.21")).thenReturn(newIndexWithRemovedFile());
        when(keywordIndexer.build(eq("3.21"), any())).thenReturn(new KeywordIndex(List.of()));

        job.refresh();

        // Only config.adoc remains in the new index
        verify(keywordIndexer).build(eq("3.21"), eq(List.of("config.adoc")));
    }

    @Test
    void refreshFetchesAllFilesWhenNoExistingIndex() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.21"));
        when(indexStore.readRaw("3.21")).thenReturn(java.util.Optional.empty());
        when(gitHubClient.fetchIndex("3.21")).thenReturn(oldIndex());
        String docJson1 = githubDocResponse("security content");
        String docJson2 = githubDocResponse("config content");
        when(gitHubClient.fetchFileContent("security-overview.adoc", "3.21")).thenReturn(docJson1);
        when(gitHubClient.fetchFileContent("config.adoc", "3.21")).thenReturn(docJson2);
        when(keywordIndexer.build(eq("3.21"), any())).thenReturn(new KeywordIndex(List.of()));

        job.refresh();

        // All files should be fetched since there's no old index to compare
        verify(gitHubClient).fetchFileContent("security-overview.adoc", "3.21");
        verify(gitHubClient).fetchFileContent("config.adoc", "3.21");
        verify(docStore).write(eq("3.21"), eq("security-overview.adoc"), eq("security content"));
        verify(docStore).write(eq("3.21"), eq("config.adoc"), eq("config content"));
    }

    @Test
    void refreshContinuesWithOtherVersionsWhenOneFails() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.20", "3.21"));
        when(gitHubClient.fetchIndex("3.20")).thenThrow(new UpstreamException("GitHub down"));
        when(indexStore.readRaw("3.21")).thenReturn(java.util.Optional.of(oldIndex()));
        when(gitHubClient.fetchIndex("3.21")).thenReturn(oldIndex());
        when(keywordIndexer.build(eq("3.21"), any())).thenReturn(new KeywordIndex(List.of()));

        job.refresh();

        // 3.21 should still be processed despite 3.20 failure
        verify(indexStore).writeRaw("3.21", oldIndex());
    }

    @Test
    void refreshDoesNotRemoveCacheOnFailure() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.21"));
        when(gitHubClient.fetchIndex("3.21")).thenThrow(new UpstreamException("GitHub down"));

        job.refresh();

        // Should NOT modify the existing index or keyword index
        verify(indexStore, never()).writeRaw(anyString(), anyString());
        verify(keywordIndexer, never()).build(anyString(), any());
    }

    @Test
    void refreshNoChangesStillUpdatesIndexAndRebuildKeywords() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.21"));
        when(indexStore.readRaw("3.21")).thenReturn(java.util.Optional.of(oldIndex()));
        when(gitHubClient.fetchIndex("3.21")).thenReturn(oldIndex());
        when(keywordIndexer.build(eq("3.21"), any())).thenReturn(new KeywordIndex(List.of()));

        job.refresh();

        // No docs should be re-fetched
        verify(gitHubClient, never()).fetchFileContent(anyString(), anyString());
        // Index should still be written
        verify(indexStore).writeRaw("3.21", oldIndex());
        // Keywords should still be rebuilt
        verify(keywordIndexer).build(eq("3.21"), eq(List.of("security-overview.adoc", "config.adoc")));
    }

    @Test
    void refreshMultipleVersions() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.20", "3.21"));
        when(indexStore.readRaw("3.20")).thenReturn(java.util.Optional.of(oldIndex()));
        when(indexStore.readRaw("3.21")).thenReturn(java.util.Optional.of(oldIndex()));
        when(gitHubClient.fetchIndex("3.20")).thenReturn(oldIndex());
        when(gitHubClient.fetchIndex("3.21")).thenReturn(oldIndex());
        when(keywordIndexer.build(eq("3.20"), any())).thenReturn(new KeywordIndex(List.of()));
        when(keywordIndexer.build(eq("3.21"), any())).thenReturn(new KeywordIndex(List.of()));

        job.refresh();

        verify(gitHubClient).fetchIndex("3.20");
        verify(gitHubClient).fetchIndex("3.21");
        verify(indexStore).writeRaw("3.20", oldIndex());
        verify(indexStore).writeRaw("3.21", oldIndex());
    }

    // -- Helper methods for building test index JSON --

    private String oldIndex() {
        return """
                [
                  {
                    "name": "security-overview.adoc",
                    "path": "docs/src/main/asciidoc/security-overview.adoc",
                    "sha": "aaa111",
                    "type": "file"
                  },
                  {
                    "name": "config.adoc",
                    "path": "docs/src/main/asciidoc/config.adoc",
                    "sha": "bbb222",
                    "type": "file"
                  }
                ]
                """;
    }

    private String newIndexWithChangedSha() {
        return """
                [
                  {
                    "name": "security-overview.adoc",
                    "path": "docs/src/main/asciidoc/security-overview.adoc",
                    "sha": "ccc333",
                    "type": "file"
                  },
                  {
                    "name": "config.adoc",
                    "path": "docs/src/main/asciidoc/config.adoc",
                    "sha": "bbb222",
                    "type": "file"
                  }
                ]
                """;
    }

    private String newIndexWithAddedFile() {
        return """
                [
                  {
                    "name": "security-overview.adoc",
                    "path": "docs/src/main/asciidoc/security-overview.adoc",
                    "sha": "aaa111",
                    "type": "file"
                  },
                  {
                    "name": "config.adoc",
                    "path": "docs/src/main/asciidoc/config.adoc",
                    "sha": "bbb222",
                    "type": "file"
                  },
                  {
                    "name": "new-file.adoc",
                    "path": "docs/src/main/asciidoc/new-file.adoc",
                    "sha": "ddd444",
                    "type": "file"
                  }
                ]
                """;
    }

    private String newIndexWithRemovedFile() {
        return """
                [
                  {
                    "name": "config.adoc",
                    "path": "docs/src/main/asciidoc/config.adoc",
                    "sha": "bbb222",
                    "type": "file"
                  }
                ]
                """;
    }

    private String githubDocResponse(String content) {
        String encoded = java.util.Base64.getEncoder().encodeToString(content.getBytes());
        return """
                {
                  "content": "%s",
                  "encoding": "base64"
                }
                """.formatted(encoded);
    }
}
