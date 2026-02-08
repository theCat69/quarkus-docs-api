package com.fvd.cache.jobs;

import com.fvd.cache.services.CacheService;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.clients.GithubApiIndex;
import com.fvd.github.exceptions.UpstreamException;
import com.fvd.github.services.ZipDownloadService;
import com.fvd.indexs.indexers.CodeSampleIndex;
import com.fvd.indexs.indexers.CodeSampleIndexer;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.indexs.services.IndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheWarmupJobTest {

    @Mock
    private DocStore docStore;

    @Mock
    private ZipDownloadService zipDownloadService;

    @Mock
    private IndexService indexService;

    @Mock
    private KeywordIndexer keywordIndexer;

    @Mock
    private CodeSampleIndexer codeSampleIndexer;

    @Mock
    private CacheService cacheService;

    private CacheWarmupJob job;

    @BeforeEach
    void setUp() {
        job = new CacheWarmupJob(docStore, zipDownloadService, indexService, keywordIndexer, codeSampleIndexer, cacheService);
        job.configuredVersions = Optional.of(List.of("3.17", "3.27"));
        job.fullReset = Optional.empty();
    }

    @Test
    void warmupDownloadsAndIndexesConfiguredVersions() {
        when(docStore.docsExist("3.17")).thenReturn(false);
        when(docStore.docsExist("3.27")).thenReturn(false);
        when(zipDownloadService.streamAndExtract("3.17")).thenReturn(List.of("security.adoc"));
        when(zipDownloadService.streamAndExtract("3.27")).thenReturn(List.of("config.adoc"));
        when(indexService.getOrFetchIndex("3.17")).thenReturn(List.of());
        when(indexService.getOrFetchIndex("3.27")).thenReturn(List.of());
        when(keywordIndexer.build(eq("3.17"), any())).thenReturn(new KeywordIndex(List.of()));
        when(keywordIndexer.build(eq("3.27"), any())).thenReturn(new KeywordIndex(List.of()));
        when(codeSampleIndexer.build(eq("3.17"), any())).thenReturn(new CodeSampleIndex(List.of()));
        when(codeSampleIndexer.build(eq("3.27"), any())).thenReturn(new CodeSampleIndex(List.of()));

        job.onStartup(null);

        verify(zipDownloadService).streamAndExtract("3.17");
        verify(zipDownloadService).streamAndExtract("3.27");
        verify(indexService).getOrFetchIndex("3.17");
        verify(indexService).getOrFetchIndex("3.27");
        verify(keywordIndexer).build(eq("3.17"), eq(List.of("security.adoc")));
        verify(keywordIndexer).build(eq("3.27"), eq(List.of("config.adoc")));
        verify(codeSampleIndexer).build(eq("3.17"), eq(List.of("security.adoc")));
        verify(codeSampleIndexer).build(eq("3.27"), eq(List.of("config.adoc")));
    }

    @Test
    void warmupSkipsVersionsWithExistingDocs() {
        when(docStore.docsExist("3.17")).thenReturn(true);
        when(docStore.docsExist("3.27")).thenReturn(false);
        when(zipDownloadService.streamAndExtract("3.27")).thenReturn(List.of("config.adoc"));
        when(indexService.getOrFetchIndex("3.27")).thenReturn(List.of());
        when(keywordIndexer.build(eq("3.27"), any())).thenReturn(new KeywordIndex(List.of()));
        when(codeSampleIndexer.build(eq("3.27"), any())).thenReturn(new CodeSampleIndex(List.of()));

        job.onStartup(null);

        verify(zipDownloadService, never()).streamAndExtract("3.17");
        verify(zipDownloadService).streamAndExtract("3.27");
    }

    @Test
    void warmupSkipsAllWhenAllVersionsCached() {
        when(docStore.docsExist("3.17")).thenReturn(true);
        when(docStore.docsExist("3.27")).thenReturn(true);

        job.onStartup(null);

        verify(zipDownloadService, never()).streamAndExtract(any());
        verify(keywordIndexer, never()).build(any(), any());
        verify(codeSampleIndexer, never()).build(any(), any());
    }

    @Test
    void warmupContinuesWhenOneVersionFails() {
        when(docStore.docsExist("3.17")).thenReturn(false);
        when(docStore.docsExist("3.27")).thenReturn(false);
        when(zipDownloadService.streamAndExtract("3.17")).thenThrow(new UpstreamException("GitHub down"));
        when(zipDownloadService.streamAndExtract("3.27")).thenReturn(List.of("config.adoc"));
        when(indexService.getOrFetchIndex("3.27")).thenReturn(List.of());
        when(keywordIndexer.build(eq("3.27"), any())).thenReturn(new KeywordIndex(List.of()));
        when(codeSampleIndexer.build(eq("3.27"), any())).thenReturn(new CodeSampleIndex(List.of()));

        job.onStartup(null);

        verify(zipDownloadService).streamAndExtract("3.17");
        verify(zipDownloadService).streamAndExtract("3.27");
        verify(keywordIndexer).build(eq("3.27"), eq(List.of("config.adoc")));
        verify(codeSampleIndexer).build(eq("3.27"), eq(List.of("config.adoc")));
    }

    @Test
    void warmupDoesNothingWhenNoVersionsConfigured() {
        job = new CacheWarmupJob(docStore, zipDownloadService, indexService, keywordIndexer, codeSampleIndexer, cacheService);
        job.configuredVersions = Optional.empty();
        job.fullReset = Optional.empty();

        job.onStartup(null);

        verify(zipDownloadService, never()).streamAndExtract(any());
        verify(indexService, never()).getOrFetchIndex(any());
        verify(keywordIndexer, never()).build(any(), any());
        verify(codeSampleIndexer, never()).build(any(), any());
    }

    @Test
    void warmupDoesNothingWhenVersionsListIsEmpty() {
        job = new CacheWarmupJob(docStore, zipDownloadService, indexService, keywordIndexer, codeSampleIndexer, cacheService);
        job.configuredVersions = Optional.of(List.of());
        job.fullReset = Optional.empty();

        job.onStartup(null);

        verify(zipDownloadService, never()).streamAndExtract(any());
    }

    @Test
    void warmupFetchesIndexAndBuildsKeywordsForExtractedFiles() {
        when(docStore.docsExist("3.17")).thenReturn(false);
        when(zipDownloadService.streamAndExtract("3.17")).thenReturn(List.of("a.adoc", "b.adoc"));
        List<GithubApiIndex> index = List.of(
                new GithubApiIndex("a.adoc", "path/a.adoc", "sha1"),
                new GithubApiIndex("b.adoc", "path/b.adoc", "sha2")
        );
        when(indexService.getOrFetchIndex("3.17")).thenReturn(index);
        when(keywordIndexer.build(eq("3.17"), any())).thenReturn(new KeywordIndex(List.of()));
        when(codeSampleIndexer.build(eq("3.17"), any())).thenReturn(new CodeSampleIndex(List.of()));

        job = new CacheWarmupJob(docStore, zipDownloadService, indexService, keywordIndexer, codeSampleIndexer, cacheService);
        job.configuredVersions = Optional.of(List.of("3.17"));
        job.fullReset = Optional.empty();

        job.onStartup(null);

        verify(indexService).getOrFetchIndex("3.17");
        verify(keywordIndexer).build("3.17", List.of("a.adoc", "b.adoc"));
        verify(codeSampleIndexer).build("3.17", List.of("a.adoc", "b.adoc"));
    }
}
