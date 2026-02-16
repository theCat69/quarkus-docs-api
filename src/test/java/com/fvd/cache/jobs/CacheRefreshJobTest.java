package com.fvd.cache.jobs;

import com.fvd.api.services.DocumentService;
import com.fvd.asciidocs.parser.AsciidocParser;
import com.fvd.cache.services.CacheService;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.clients.GithubApiFile;
import com.fvd.github.clients.GithubApiIndex;
import com.fvd.github.exceptions.UpstreamException;
import com.fvd.github.services.GitHubService;
import com.fvd.indexs.services.DocChunkBuilder;
import com.fvd.indexs.stores.IndexStore;
import com.fvd.quarkiverse.services.QuarkiverseService;
import com.fvd.search.TestSearchConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheRefreshJobTest {

    @Mock
    private CacheService cacheService;

    @Mock
    private GitHubService gitHubService;

    @Mock
    private IndexStore indexStore;

    @Mock
    private DocStore docStore;

    @Mock
    private DocChunkBuilder docChunkBuilder;

    @Mock
    private QuarkiverseService quarkiverseService;

    @Mock
    private DocumentService documentService;

    private final DocParser docParser = new AsciidocParser(new TestSearchConfig());

    private CacheRefreshJob job;

    @BeforeEach
    void setUp() {
        job = new CacheRefreshJob(cacheService, gitHubService, indexStore, docStore, docChunkBuilder, docParser, quarkiverseService, documentService);
        job.quarkiverseEnabled = false;
    }

    @Test
    void refreshSkipsWhenNoCachedVersions() {
        when(cacheService.listCachedVersions()).thenReturn(List.of());

        job.refresh();

        verify(gitHubService, never()).fetchIndex(anyString());
    }

    @Test
    void refreshFetchesNewIndexForCachedVersion() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.27"));
        when(indexStore.read("3.27")).thenReturn(Optional.of(oldIndex()));
        when(gitHubService.fetchIndex("3.27")).thenReturn(oldIndex());

        job.refresh();

        verify(gitHubService).fetchIndex("3.27");
    }

    @Test
    void refreshDetectsChangedFilesAndRefetches() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.27"));
        when(indexStore.read("3.27")).thenReturn(Optional.of(oldIndex()));
        when(gitHubService.fetchIndex("3.27")).thenReturn(newIndexWithChangedSha());
        GithubApiFile docFile = githubDocFile("updated content");
        when(gitHubService.fetchFileContent("_versions/3.27/guides/security-overview.adoc", "3.27")).thenReturn(docFile);

        job.refresh();

        // security-overview.adoc has a changed SHA, should be re-fetched and written
        // The full GitHub API path is used for fetching, but the docs prefix is stripped for storage
        verify(gitHubService).fetchFileContent("_versions/3.27/guides/security-overview.adoc", "3.27");
        verify(docStore).write(eq("3.27"), eq("security-overview.adoc"), eq("updated content"));
        // config.adoc has the same SHA, should NOT be re-fetched
        verify(gitHubService, never()).fetchFileContent(eq("config.adoc"), anyString());
    }

    @Test
    void refreshReplacesFileIndexWithNewData() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.27"));
        when(indexStore.read("3.27")).thenReturn(Optional.of(oldIndex()));
        List<GithubApiIndex> newIndex = newIndexWithChangedSha();
        when(gitHubService.fetchIndex("3.27")).thenReturn(newIndex);
        GithubApiFile docFile = githubDocFile("updated content");
        when(gitHubService.fetchFileContent("_versions/3.27/guides/security-overview.adoc", "3.27")).thenReturn(docFile);

        job.refresh();

        verify(indexStore).write("3.27", newIndex);
    }

    @Test
    void refreshRebuildsDocChunksAfterUpdate() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.27"));
        when(indexStore.read("3.27")).thenReturn(Optional.of(oldIndex()));
        when(gitHubService.fetchIndex("3.27")).thenReturn(newIndexWithChangedSha());
        GithubApiFile docFile = githubDocFile("updated content");
        when(gitHubService.fetchFileContent("_versions/3.27/guides/security-overview.adoc", "3.27")).thenReturn(docFile);

        job.refresh();

        verify(docChunkBuilder).build(eq("3.27"), eq(List.of("security-overview.adoc", "config.adoc")));
        verify(documentService).invalidateDocumentCache("3.27");
    }

    @Test
    void refreshInvalidatesDocumentCacheForVersion() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.27"));
        when(indexStore.read("3.27")).thenReturn(Optional.of(oldIndex()));
        when(gitHubService.fetchIndex("3.27")).thenReturn(oldIndex());

        job.refresh();

        verify(documentService).invalidateDocumentCache("3.27");
    }

    @Test
    void refreshHandlesNewFilesInIndex() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.27"));
        when(indexStore.read("3.27")).thenReturn(Optional.of(oldIndex()));
        when(gitHubService.fetchIndex("3.27")).thenReturn(newIndexWithAddedFile());
        GithubApiFile docFile = githubDocFile("new file content");
        when(gitHubService.fetchFileContent("_versions/3.27/guides/new-file.adoc", "3.27")).thenReturn(docFile);

        job.refresh();

        // new-file.adoc is not in the old index, should be fetched
        verify(gitHubService).fetchFileContent("_versions/3.27/guides/new-file.adoc", "3.27");
        verify(docStore).write(eq("3.27"), eq("new-file.adoc"), eq("new file content"));
    }

    @Test
    void refreshHandlesRemovedFilesInIndex() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.27"));
        when(indexStore.read("3.27")).thenReturn(Optional.of(oldIndex()));
        when(gitHubService.fetchIndex("3.27")).thenReturn(newIndexWithRemovedFile());

        job.refresh();

        // Only config.adoc remains in the new index
        verify(docChunkBuilder).build(eq("3.27"), eq(List.of("config.adoc")));
    }

    @Test
    void refreshFetchesAllFilesWhenNoExistingIndex() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.27"));
        when(indexStore.read("3.27")).thenReturn(Optional.empty());
        when(gitHubService.fetchIndex("3.27")).thenReturn(oldIndex());
        GithubApiFile docFile1 = githubDocFile("security content");
        GithubApiFile docFile2 = githubDocFile("config content");
        when(gitHubService.fetchFileContent("_versions/3.27/guides/security-overview.adoc", "3.27")).thenReturn(docFile1);
        when(gitHubService.fetchFileContent("_versions/3.27/guides/config.adoc", "3.27")).thenReturn(docFile2);

        job.refresh();

        // All files should be fetched since there's no old index to compare
        verify(gitHubService).fetchFileContent("_versions/3.27/guides/security-overview.adoc", "3.27");
        verify(gitHubService).fetchFileContent("_versions/3.27/guides/config.adoc", "3.27");
        verify(docStore).write(eq("3.27"), eq("security-overview.adoc"), eq("security content"));
        verify(docStore).write(eq("3.27"), eq("config.adoc"), eq("config content"));
    }

    @Test
    void refreshContinuesWithOtherVersionsWhenOneFails() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.20", "3.27"));
        when(gitHubService.fetchIndex("3.20")).thenThrow(new UpstreamException("GitHub down"));
        when(indexStore.read("3.27")).thenReturn(Optional.of(oldIndex()));
        when(gitHubService.fetchIndex("3.27")).thenReturn(oldIndex());

        job.refresh();

        // 3.27 should still be processed despite 3.20 failure
        verify(indexStore).write("3.27", oldIndex());
    }

    @Test
    void refreshDoesNotRemoveCacheOnFailure() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.27"));
        when(gitHubService.fetchIndex("3.27")).thenThrow(new UpstreamException("GitHub down"));

        job.refresh();

        // Should NOT modify the existing index or doc chunks
        verify(indexStore, never()).write(anyString(), anyList());
        verify(docChunkBuilder, never()).build(anyString(), anyList());
    }

    @Test
    void refreshNoChangesStillUpdatesIndexAndRebuildDocChunks() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.27"));
        when(indexStore.read("3.27")).thenReturn(Optional.of(oldIndex()));
        when(gitHubService.fetchIndex("3.27")).thenReturn(oldIndex());

        job.refresh();

        // No docs should be re-fetched
        verify(gitHubService, never()).fetchFileContent(anyString(), anyString());
        // Index should still be written
        verify(indexStore).write("3.27", oldIndex());
        // Doc chunks should still be rebuilt
        verify(docChunkBuilder).build(eq("3.27"), eq(List.of("security-overview.adoc", "config.adoc")));
    }

    @Test
    void refreshMultipleVersions() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.20", "3.27"));
        when(indexStore.read("3.20")).thenReturn(Optional.of(oldIndex()));
        when(indexStore.read("3.27")).thenReturn(Optional.of(oldIndex()));
        when(gitHubService.fetchIndex("3.20")).thenReturn(oldIndex());
        when(gitHubService.fetchIndex("3.27")).thenReturn(oldIndex());

        job.refresh();

        verify(gitHubService).fetchIndex("3.20");
        verify(gitHubService).fetchIndex("3.27");
        verify(indexStore).write("3.20", oldIndex());
        verify(indexStore).write("3.27", oldIndex());
    }

    // -- Helper methods for building test data --

    private List<GithubApiIndex> oldIndex() {
        return List.of(
                new GithubApiIndex("security-overview.adoc",
                        "_versions/3.27/guides/security-overview.adoc", "aaa111"),
                new GithubApiIndex("config.adoc",
                        "_versions/3.27/guides/config.adoc", "bbb222")
        );
    }

    private List<GithubApiIndex> newIndexWithChangedSha() {
        return List.of(
                new GithubApiIndex("security-overview.adoc",
                        "_versions/3.27/guides/security-overview.adoc", "ccc333"),
                new GithubApiIndex("config.adoc",
                        "_versions/3.27/guides/config.adoc", "bbb222")
        );
    }

    private List<GithubApiIndex> newIndexWithAddedFile() {
        return List.of(
                new GithubApiIndex("security-overview.adoc",
                        "_versions/3.27/guides/security-overview.adoc", "aaa111"),
                new GithubApiIndex("config.adoc",
                        "_versions/3.27/guides/config.adoc", "bbb222"),
                new GithubApiIndex("new-file.adoc",
                        "_versions/3.27/guides/new-file.adoc", "ddd444")
        );
    }

    private List<GithubApiIndex> newIndexWithRemovedFile() {
        return List.of(
                new GithubApiIndex("config.adoc",
                        "_versions/3.27/guides/config.adoc", "bbb222")
        );
    }

    private GithubApiFile githubDocFile(String content) {
        String encoded = Base64.getEncoder().encodeToString(content.getBytes());
        return new GithubApiFile("file.adoc", "path/file.adoc", "sha1", encoded, "base64");
    }

    // -- Quarkiverse integration tests --

    @Test
    void refreshWithQuarkiverseEnabledCallsRefreshAllForMainVersion() {
        job.quarkiverseEnabled = true;

        when(cacheService.listCachedVersions()).thenReturn(List.of("main"));
        when(indexStore.read("main")).thenReturn(Optional.of(mainIndex()));
        when(gitHubService.fetchIndex("main")).thenReturn(mainIndex());

        // Quarkiverse refresh returns true (changes detected)
        when(quarkiverseService.refreshAll()).thenReturn(true);

        // After quarkiverse refresh, docStore.listDocFiles is called to get all files
        when(docStore.listDocFiles("main")).thenReturn(List.of(
                "config.adoc", "security-overview.adoc",
                "quarkiverse/quarkus-openapi-generator/index.adoc"));

        job.refresh();

        verify(quarkiverseService).refreshAll();

        // Verify Map overload is used for the rebuild with correct grouping
        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("quarkus-core", List.of("config.adoc", "security-overview.adoc"));
        expected.put("quarkus-openapi-generator", List.of("quarkiverse/quarkus-openapi-generator/index.adoc"));
        verify(docChunkBuilder).build(eq("main"), eq(expected));
        verify(documentService, times(2)).invalidateDocumentCache("main");
    }

    @Test
    void refreshWithQuarkiverseEnabledNoChangesDoesNotRebuildIndexes() {
        job.quarkiverseEnabled = true;

        when(cacheService.listCachedVersions()).thenReturn(List.of("main"));
        when(indexStore.read("main")).thenReturn(Optional.of(mainIndex()));
        when(gitHubService.fetchIndex("main")).thenReturn(mainIndex());

        // Quarkiverse refresh returns false (no changes)
        when(quarkiverseService.refreshAll()).thenReturn(false);

        job.refresh();

        verify(quarkiverseService).refreshAll();
        // Map overload should NOT be called since no changes
        verify(docChunkBuilder, never()).build(eq("main"), anyMap());
    }

    @Test
    void refreshWithQuarkiverseDisabledDoesNotCallRefreshAll() {
        job.quarkiverseEnabled = false;

        when(cacheService.listCachedVersions()).thenReturn(List.of("main"));
        when(indexStore.read("main")).thenReturn(Optional.of(mainIndex()));
        when(gitHubService.fetchIndex("main")).thenReturn(mainIndex());

        job.refresh();

        verify(quarkiverseService, never()).refreshAll();
    }

    @Test
    void refreshWithQuarkiverseEnabledButNoMainVersionSkipsQuarkiverse() {
        job.quarkiverseEnabled = true;

        when(cacheService.listCachedVersions()).thenReturn(List.of("3.27"));
        when(indexStore.read("3.27")).thenReturn(Optional.of(oldIndex()));
        when(gitHubService.fetchIndex("3.27")).thenReturn(oldIndex());

        job.refresh();

        verify(quarkiverseService, never()).refreshAll();
    }

    @Test
    void refreshWithQuarkiverseFailureDoesNotFailCoreRefresh() {
        job.quarkiverseEnabled = true;

        when(cacheService.listCachedVersions()).thenReturn(List.of("main"));
        when(indexStore.read("main")).thenReturn(Optional.of(mainIndex()));
        when(gitHubService.fetchIndex("main")).thenReturn(mainIndex());

        // Quarkiverse refresh throws exception
        when(quarkiverseService.refreshAll()).thenThrow(new RuntimeException("Quarkiverse down"));

        job.refresh();

        // Core refresh should have completed normally
        verify(indexStore).write("main", mainIndex());
        verify(docChunkBuilder).build(eq("main"), anyList());
        // Map overload should NOT be called since quarkiverse failed
        verify(docChunkBuilder, never()).build(eq("main"), anyMap());
    }

    @Test
    void refreshWithQuarkiverseEnabledAndMultipleVersionsOnlyRefreshesMain() {
        job.quarkiverseEnabled = true;

        when(cacheService.listCachedVersions()).thenReturn(List.of("3.27", "main"));
        when(indexStore.read("3.27")).thenReturn(Optional.of(oldIndex()));
        when(indexStore.read("main")).thenReturn(Optional.of(mainIndex()));
        when(gitHubService.fetchIndex("3.27")).thenReturn(oldIndex());
        when(gitHubService.fetchIndex("main")).thenReturn(mainIndex());

        // Quarkiverse refresh returns true
        when(quarkiverseService.refreshAll()).thenReturn(true);
        when(docStore.listDocFiles("main")).thenReturn(List.of(
                "config.adoc", "quarkiverse/quarkus-cxf/index.adoc"));

        job.refresh();

        // Both versions refreshed normally
        verify(gitHubService).fetchIndex("3.27");
        verify(gitHubService).fetchIndex("main");

        // Only main gets quarkiverse merge
        verify(quarkiverseService).refreshAll();
        verify(docChunkBuilder).build(eq("main"), anyMap());

        // 3.27 uses List overload only
        verify(docChunkBuilder).build(eq("3.27"), anyList());
    }

    @Test
    void refreshWithQuarkiverseGroupsFilesByExtension() {
        job.quarkiverseEnabled = true;

        when(cacheService.listCachedVersions()).thenReturn(List.of("main"));
        when(indexStore.read("main")).thenReturn(Optional.of(mainIndex()));
        when(gitHubService.fetchIndex("main")).thenReturn(mainIndex());

        when(quarkiverseService.refreshAll()).thenReturn(true);
        when(docStore.listDocFiles("main")).thenReturn(List.of(
                "config.adoc",
                "quarkiverse/quarkus-cxf/index.adoc",
                "quarkiverse/quarkus-cxf/usage.adoc",
                "quarkiverse/quarkus-openapi-generator/index.adoc",
                "security-overview.adoc"));

        job.refresh();

        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("quarkus-core", List.of("config.adoc", "security-overview.adoc"));
        expected.put("quarkus-cxf", List.of(
                "quarkiverse/quarkus-cxf/index.adoc",
                "quarkiverse/quarkus-cxf/usage.adoc"));
        expected.put("quarkus-openapi-generator", List.of(
                "quarkiverse/quarkus-openapi-generator/index.adoc"));

        verify(docChunkBuilder).build(eq("main"), eq(expected));
    }

    private List<GithubApiIndex> mainIndex() {
        return List.of(
                new GithubApiIndex("security-overview.adoc",
                        "docs/modules/ROOT/pages/security-overview.adoc", "aaa111"),
                new GithubApiIndex("config.adoc",
                        "docs/modules/ROOT/pages/config.adoc", "bbb222")
        );
    }
}
