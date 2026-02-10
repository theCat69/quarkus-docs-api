package com.fvd.cache.jobs;

import com.fvd.asciidocs.parser.AsciidocParser;
import com.fvd.cache.services.CacheService;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.clients.GithubApiFile;
import com.fvd.github.clients.GithubApiIndex;
import com.fvd.github.exceptions.UpstreamException;
import com.fvd.github.services.GitHubService;
import com.fvd.indexs.indexers.CodeSampleIndexer;
import com.fvd.indexs.indexers.ContentIndexer;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.indexs.stores.IndexStore;
import com.fvd.search.SearchConfig;
import com.fvd.search.TestSearchConfig;
import com.fvd.search.services.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.List;
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
    private KeywordIndexer keywordIndexer;

    @Mock
    private CodeSampleIndexer codeSampleIndexer;

    @Mock
    private ContentIndexer contentIndexer;

    @Mock
    private SearchService searchService;

    private final DocParser docParser = new AsciidocParser(new TestSearchConfig());

    private CacheRefreshJob job;

    @BeforeEach
    void setUp() {
        job = new CacheRefreshJob(cacheService, gitHubService, indexStore, docStore, keywordIndexer, codeSampleIndexer, contentIndexer, searchService, docParser);
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
        when(keywordIndexer.build(eq("3.27"), any())).thenReturn(new KeywordIndex(List.of()));

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
        when(keywordIndexer.build(eq("3.27"), any())).thenReturn(new KeywordIndex(List.of()));

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
        when(keywordIndexer.build(eq("3.27"), any())).thenReturn(new KeywordIndex(List.of()));

        job.refresh();

        verify(indexStore).write("3.27", newIndex);
    }

    @Test
    void refreshRebuildsKeywordIndexAfterUpdate() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.27"));
        when(indexStore.read("3.27")).thenReturn(Optional.of(oldIndex()));
        when(gitHubService.fetchIndex("3.27")).thenReturn(newIndexWithChangedSha());
        GithubApiFile docFile = githubDocFile("updated content");
        when(gitHubService.fetchFileContent("_versions/3.27/guides/security-overview.adoc", "3.27")).thenReturn(docFile);
        when(keywordIndexer.build(eq("3.27"), any())).thenReturn(new KeywordIndex(List.of()));

        job.refresh();

        verify(keywordIndexer).build(eq("3.27"), eq(List.of("security-overview.adoc", "config.adoc")));
        verify(codeSampleIndexer).build(eq("3.27"), eq(List.of("security-overview.adoc", "config.adoc")));
        verify(contentIndexer).build(eq("3.27"), eq(List.of("security-overview.adoc", "config.adoc")));
        verify(searchService).invalidateCache("3.27");
    }

    @Test
    void refreshHandlesNewFilesInIndex() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.27"));
        when(indexStore.read("3.27")).thenReturn(Optional.of(oldIndex()));
        when(gitHubService.fetchIndex("3.27")).thenReturn(newIndexWithAddedFile());
        GithubApiFile docFile = githubDocFile("new file content");
        when(gitHubService.fetchFileContent("_versions/3.27/guides/new-file.adoc", "3.27")).thenReturn(docFile);
        when(keywordIndexer.build(eq("3.27"), any())).thenReturn(new KeywordIndex(List.of()));

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
        when(keywordIndexer.build(eq("3.27"), any())).thenReturn(new KeywordIndex(List.of()));

        job.refresh();

        // Only config.adoc remains in the new index
        verify(keywordIndexer).build(eq("3.27"), eq(List.of("config.adoc")));
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
        when(keywordIndexer.build(eq("3.27"), any())).thenReturn(new KeywordIndex(List.of()));

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
        when(keywordIndexer.build(eq("3.27"), any())).thenReturn(new KeywordIndex(List.of()));

        job.refresh();

        // 3.27 should still be processed despite 3.20 failure
        verify(indexStore).write("3.27", oldIndex());
    }

    @Test
    void refreshDoesNotRemoveCacheOnFailure() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.27"));
        when(gitHubService.fetchIndex("3.27")).thenThrow(new UpstreamException("GitHub down"));

        job.refresh();

        // Should NOT modify the existing index or keyword index
        verify(indexStore, never()).write(anyString(), anyList());
        verify(keywordIndexer, never()).build(anyString(), any());
        verify(codeSampleIndexer, never()).build(anyString(), any());
        verify(contentIndexer, never()).build(anyString(), any());
    }

    @Test
    void refreshNoChangesStillUpdatesIndexAndRebuildKeywords() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.27"));
        when(indexStore.read("3.27")).thenReturn(Optional.of(oldIndex()));
        when(gitHubService.fetchIndex("3.27")).thenReturn(oldIndex());
        when(keywordIndexer.build(eq("3.27"), any())).thenReturn(new KeywordIndex(List.of()));

        job.refresh();

        // No docs should be re-fetched
        verify(gitHubService, never()).fetchFileContent(anyString(), anyString());
        // Index should still be written
        verify(indexStore).write("3.27", oldIndex());
        // Keywords should still be rebuilt
        verify(keywordIndexer).build(eq("3.27"), eq(List.of("security-overview.adoc", "config.adoc")));
    }

    @Test
    void refreshMultipleVersions() {
        when(cacheService.listCachedVersions()).thenReturn(List.of("3.20", "3.27"));
        when(indexStore.read("3.20")).thenReturn(Optional.of(oldIndex()));
        when(indexStore.read("3.27")).thenReturn(Optional.of(oldIndex()));
        when(gitHubService.fetchIndex("3.20")).thenReturn(oldIndex());
        when(gitHubService.fetchIndex("3.27")).thenReturn(oldIndex());
        when(keywordIndexer.build(eq("3.20"), any())).thenReturn(new KeywordIndex(List.of()));
        when(keywordIndexer.build(eq("3.27"), any())).thenReturn(new KeywordIndex(List.of()));

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
}
