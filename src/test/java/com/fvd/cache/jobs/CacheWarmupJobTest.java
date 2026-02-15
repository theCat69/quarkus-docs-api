package com.fvd.cache.jobs;

import com.fvd.cache.services.CacheService;
import com.fvd.cache.services.WarmupStatusTracker;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.clients.GithubApiIndex;
import com.fvd.github.exceptions.UpstreamException;
import com.fvd.github.services.ZipDownloadService;
import com.fvd.indexs.indexers.CodeSampleIndex;
import com.fvd.indexs.indexers.CodeSampleIndexer;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.indexs.services.IndexService;
import com.fvd.quarkiverse.services.QuarkiverseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Mock
    private QuarkiverseService quarkiverseService;

    private WarmupStatusTracker warmupStatusTracker;

    private CacheWarmupJob job;

    @BeforeEach
    void setUp() {
        warmupStatusTracker = new WarmupStatusTracker();
        job = new CacheWarmupJob(docStore, zipDownloadService, indexService, keywordIndexer, codeSampleIndexer, cacheService, quarkiverseService, warmupStatusTracker);
        job.configuredVersions = Optional.of(List.of("3.17", "3.27"));
        job.fullReset = Optional.empty();
        job.quarkiverseEnabled = false;
    }

    @Test
    void warmupDownloadsAndIndexesConfiguredVersions() {
        when(docStore.docsExist("3.17")).thenReturn(false);
        when(docStore.docsExist("3.27")).thenReturn(false);
        when(zipDownloadService.streamAndExtractAll(List.of("3.17", "3.27")))
                .thenReturn(Map.of("3.17", List.of("security.adoc"), "3.27", List.of("config.adoc")));
        when(indexService.getOrFetchIndex("3.17")).thenReturn(List.of());
        when(indexService.getOrFetchIndex("3.27")).thenReturn(List.of());
        when(keywordIndexer.build(eq("3.17"), anyList())).thenReturn(new KeywordIndex(List.of()));
        when(keywordIndexer.build(eq("3.27"), anyList())).thenReturn(new KeywordIndex(List.of()));
        when(codeSampleIndexer.build(eq("3.17"), anyList())).thenReturn(new CodeSampleIndex(List.of()));
        when(codeSampleIndexer.build(eq("3.27"), anyList())).thenReturn(new CodeSampleIndex(List.of()));

        job.onStartup(null);

        verify(zipDownloadService).streamAndExtractAll(List.of("3.17", "3.27"));
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
        when(zipDownloadService.streamAndExtractAll(List.of("3.27")))
                .thenReturn(Map.of("3.27", List.of("config.adoc")));
        when(indexService.getOrFetchIndex("3.27")).thenReturn(List.of());
        when(keywordIndexer.build(eq("3.27"), anyList())).thenReturn(new KeywordIndex(List.of()));
        when(codeSampleIndexer.build(eq("3.27"), anyList())).thenReturn(new CodeSampleIndex(List.of()));

        job.onStartup(null);

        verify(zipDownloadService, never()).streamAndExtractAll(List.of("3.17", "3.27"));
        verify(zipDownloadService).streamAndExtractAll(List.of("3.27"));
    }

    @Test
    void warmupSkipsAllWhenAllVersionsCached() {
        when(docStore.docsExist("3.17")).thenReturn(true);
        when(docStore.docsExist("3.27")).thenReturn(true);

        job.onStartup(null);

        verify(zipDownloadService, never()).streamAndExtractAll(any());
        verify(keywordIndexer, never()).build(anyString(), anyList());
        verify(codeSampleIndexer, never()).build(anyString(), anyList());

        // Tracker should show consistent state: total matches completed, ready is true
        assertThat(warmupStatusTracker.isReady()).isTrue();
        assertThat(warmupStatusTracker.getTotalVersions()).isZero();
        assertThat(warmupStatusTracker.getCompletedCount()).isZero();
    }

    @Test
    void warmupContinuesWhenIndexBuildFailsForOneVersion() {
        when(docStore.docsExist("3.17")).thenReturn(false);
        when(docStore.docsExist("3.27")).thenReturn(false);
        when(zipDownloadService.streamAndExtractAll(List.of("3.17", "3.27")))
                .thenReturn(Map.of("3.17", List.of("security.adoc"), "3.27", List.of("config.adoc")));
        when(indexService.getOrFetchIndex("3.17")).thenThrow(new UpstreamException("GitHub down"));
        when(indexService.getOrFetchIndex("3.27")).thenReturn(List.of());
        when(keywordIndexer.build(eq("3.27"), anyList())).thenReturn(new KeywordIndex(List.of()));
        when(codeSampleIndexer.build(eq("3.27"), anyList())).thenReturn(new CodeSampleIndex(List.of()));

        job.onStartup(null);

        verify(zipDownloadService).streamAndExtractAll(List.of("3.17", "3.27"));
        verify(keywordIndexer).build(eq("3.27"), eq(List.of("config.adoc")));
        verify(codeSampleIndexer).build(eq("3.27"), eq(List.of("config.adoc")));
    }

    @Test
    void warmupDoesNothingWhenNoVersionsConfigured() {
        job = new CacheWarmupJob(docStore, zipDownloadService, indexService, keywordIndexer, codeSampleIndexer, cacheService, quarkiverseService, warmupStatusTracker);
        job.configuredVersions = Optional.empty();
        job.fullReset = Optional.empty();
        job.quarkiverseEnabled = false;

        job.onStartup(null);

        verify(zipDownloadService, never()).streamAndExtractAll(any());
        verify(indexService, never()).getOrFetchIndex(any());
        verify(keywordIndexer, never()).build(anyString(), anyList());
        verify(codeSampleIndexer, never()).build(anyString(), anyList());
    }

    @Test
    void warmupDoesNothingWhenVersionsListIsEmpty() {
        job = new CacheWarmupJob(docStore, zipDownloadService, indexService, keywordIndexer, codeSampleIndexer, cacheService, quarkiverseService, warmupStatusTracker);
        job.configuredVersions = Optional.of(List.of());
        job.fullReset = Optional.empty();
        job.quarkiverseEnabled = false;

        job.onStartup(null);

        verify(zipDownloadService, never()).streamAndExtractAll(any());
    }

    @Test
    void warmupFetchesIndexAndBuildsKeywordsForExtractedFiles() {
        when(docStore.docsExist("3.17")).thenReturn(false);
        when(zipDownloadService.streamAndExtractAll(List.of("3.17")))
                .thenReturn(Map.of("3.17", List.of("a.adoc", "b.adoc")));
        List<GithubApiIndex> index = List.of(
                new GithubApiIndex("a.adoc", "path/a.adoc", "sha1"),
                new GithubApiIndex("b.adoc", "path/b.adoc", "sha2")
        );
        when(indexService.getOrFetchIndex("3.17")).thenReturn(index);
        when(keywordIndexer.build(eq("3.17"), anyList())).thenReturn(new KeywordIndex(List.of()));
        when(codeSampleIndexer.build(eq("3.17"), anyList())).thenReturn(new CodeSampleIndex(List.of()));

        job = new CacheWarmupJob(docStore, zipDownloadService, indexService, keywordIndexer, codeSampleIndexer, cacheService, quarkiverseService, warmupStatusTracker);
        job.configuredVersions = Optional.of(List.of("3.17"));
        job.fullReset = Optional.empty();
        job.quarkiverseEnabled = false;

        job.onStartup(null);

        verify(indexService).getOrFetchIndex("3.17");
        verify(keywordIndexer).build("3.17", List.of("a.adoc", "b.adoc"));
        verify(codeSampleIndexer).build("3.17", List.of("a.adoc", "b.adoc"));
    }

    // -- Quarkiverse integration tests --

    @Test
    void warmupWithQuarkiverseEnabledMergesMainIndexes() {
        job.configuredVersions = Optional.of(List.of("main"));
        job.quarkiverseEnabled = true;

        when(docStore.docsExist("main")).thenReturn(false);
        when(zipDownloadService.streamAndExtractAll(List.of("main")))
                .thenReturn(Map.of("main", List.of("security.adoc", "config.adoc")));
        when(indexService.getOrFetchIndex("main")).thenReturn(List.of());
        when(quarkiverseService.fetchAndExtractAll())
                .thenReturn(List.of("quarkiverse/quarkus-openapi-generator/index.adoc"));

        // The Map overload is used for merged builds
        when(keywordIndexer.build(eq("main"), anyMap())).thenReturn(new KeywordIndex(List.of()));
        when(codeSampleIndexer.build(eq("main"), anyMap())).thenReturn(new CodeSampleIndex(List.of()));

        job.onStartup(null);

        // Verify quarkiverse was called
        verify(quarkiverseService).fetchAndExtractAll();

        // Verify indexes are built with Map overload (merged core + quarkiverse)
        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("quarkus-core", List.of("security.adoc", "config.adoc"));
        expected.put("quarkus-openapi-generator", List.of("quarkiverse/quarkus-openapi-generator/index.adoc"));
        verify(keywordIndexer).build(eq("main"), eq(expected));
        verify(codeSampleIndexer).build(eq("main"), eq(expected));

        // Verify List overload was NOT called for "main"
        verify(keywordIndexer, never()).build(eq("main"), anyList());
        verify(codeSampleIndexer, never()).build(eq("main"), anyList());
    }

    @Test
    void warmupWithQuarkiverseDisabledDoesNotCallQuarkiverse() {
        job.configuredVersions = Optional.of(List.of("main"));
        job.quarkiverseEnabled = false;

        when(docStore.docsExist("main")).thenReturn(false);
        when(zipDownloadService.streamAndExtractAll(List.of("main")))
                .thenReturn(Map.of("main", List.of("security.adoc")));
        when(indexService.getOrFetchIndex("main")).thenReturn(List.of());
        when(keywordIndexer.build(eq("main"), anyList())).thenReturn(new KeywordIndex(List.of()));
        when(codeSampleIndexer.build(eq("main"), anyList())).thenReturn(new CodeSampleIndex(List.of()));

        job.onStartup(null);

        // Quarkiverse should not be called
        verify(quarkiverseService, never()).fetchAndExtractAll();

        // Indexes should be built with List overload (core only)
        verify(keywordIndexer).build(eq("main"), eq(List.of("security.adoc")));
    }

    @Test
    void warmupWithQuarkiverseEnabledButNoMainVersionSkipsQuarkiverse() {
        job.configuredVersions = Optional.of(List.of("3.27"));
        job.quarkiverseEnabled = true;

        when(docStore.docsExist("3.27")).thenReturn(false);
        when(zipDownloadService.streamAndExtractAll(List.of("3.27")))
                .thenReturn(Map.of("3.27", List.of("config.adoc")));
        when(indexService.getOrFetchIndex("3.27")).thenReturn(List.of());
        when(keywordIndexer.build(eq("3.27"), anyList())).thenReturn(new KeywordIndex(List.of()));
        when(codeSampleIndexer.build(eq("3.27"), anyList())).thenReturn(new CodeSampleIndex(List.of()));

        job.onStartup(null);

        // Quarkiverse should not be called since "main" isn't in versions
        verify(quarkiverseService, never()).fetchAndExtractAll();
    }

    @Test
    void warmupWithQuarkiverseEnabledBuildsNonMainVersionsNormally() {
        job.configuredVersions = Optional.of(List.of("main", "3.27"));
        job.quarkiverseEnabled = true;

        when(docStore.docsExist("main")).thenReturn(false);
        when(docStore.docsExist("3.27")).thenReturn(false);
        when(zipDownloadService.streamAndExtractAll(List.of("main", "3.27")))
                .thenReturn(Map.of("main", List.of("security.adoc"), "3.27", List.of("config.adoc")));
        when(indexService.getOrFetchIndex("main")).thenReturn(List.of());
        when(indexService.getOrFetchIndex("3.27")).thenReturn(List.of());
        when(quarkiverseService.fetchAndExtractAll())
                .thenReturn(List.of("quarkiverse/quarkus-cxf/index.adoc"));

        // 3.27 uses List overload
        when(keywordIndexer.build(eq("3.27"), anyList())).thenReturn(new KeywordIndex(List.of()));
        when(codeSampleIndexer.build(eq("3.27"), anyList())).thenReturn(new CodeSampleIndex(List.of()));
        // main uses Map overload
        when(keywordIndexer.build(eq("main"), anyMap())).thenReturn(new KeywordIndex(List.of()));
        when(codeSampleIndexer.build(eq("main"), anyMap())).thenReturn(new CodeSampleIndex(List.of()));

        job.onStartup(null);

        // 3.27 should use List overload (core only)
        verify(keywordIndexer).build(eq("3.27"), eq(List.of("config.adoc")));
        verify(codeSampleIndexer).build(eq("3.27"), eq(List.of("config.adoc")));

        // main should use Map overload (merged)
        verify(keywordIndexer).build(eq("main"), anyMap());
        verify(codeSampleIndexer).build(eq("main"), anyMap());
    }

    @Test
    void warmupWithQuarkiverseFailureStillBuildsMainCoreIndexes() {
        job.configuredVersions = Optional.of(List.of("main"));
        job.quarkiverseEnabled = true;

        when(docStore.docsExist("main")).thenReturn(false);
        when(zipDownloadService.streamAndExtractAll(List.of("main")))
                .thenReturn(Map.of("main", List.of("security.adoc")));
        when(indexService.getOrFetchIndex("main")).thenReturn(List.of());
        when(quarkiverseService.fetchAndExtractAll())
                .thenThrow(new RuntimeException("Quarkiverse fetch failed"));

        // When quarkiverse fails, fallback to core-only List overload
        when(keywordIndexer.build(eq("main"), anyList())).thenReturn(new KeywordIndex(List.of()));
        when(codeSampleIndexer.build(eq("main"), anyList())).thenReturn(new CodeSampleIndex(List.of()));

        job.onStartup(null);

        // Core indexes should still be built even when quarkiverse fails
        verify(keywordIndexer).build(eq("main"), eq(List.of("security.adoc")));
        verify(codeSampleIndexer).build(eq("main"), eq(List.of("security.adoc")));
    }

    @Test
    void warmupWithQuarkiverseReturningEmptyListBuildsMainCoreOnly() {
        job.configuredVersions = Optional.of(List.of("main"));
        job.quarkiverseEnabled = true;

        when(docStore.docsExist("main")).thenReturn(false);
        when(zipDownloadService.streamAndExtractAll(List.of("main")))
                .thenReturn(Map.of("main", List.of("security.adoc")));
        when(indexService.getOrFetchIndex("main")).thenReturn(List.of());
        when(quarkiverseService.fetchAndExtractAll()).thenReturn(List.of());

        // When quarkiverse returns empty, use List overload (core only)
        when(keywordIndexer.build(eq("main"), anyList())).thenReturn(new KeywordIndex(List.of()));
        when(codeSampleIndexer.build(eq("main"), anyList())).thenReturn(new CodeSampleIndex(List.of()));

        job.onStartup(null);

        verify(keywordIndexer).build(eq("main"), eq(List.of("security.adoc")));
    }

    @Test
    void warmupWithQuarkiverseGroupsPathsByExtension() {
        job.configuredVersions = Optional.of(List.of("main"));
        job.quarkiverseEnabled = true;

        when(docStore.docsExist("main")).thenReturn(false);
        when(zipDownloadService.streamAndExtractAll(List.of("main")))
                .thenReturn(Map.of("main", List.of("security.adoc")));
        when(indexService.getOrFetchIndex("main")).thenReturn(List.of());
        when(quarkiverseService.fetchAndExtractAll())
                .thenReturn(List.of(
                        "quarkiverse/quarkus-openapi-generator/index.adoc",
                        "quarkiverse/quarkus-openapi-generator/usage.adoc",
                        "quarkiverse/quarkus-cxf/index.adoc"
                ));

        when(keywordIndexer.build(eq("main"), anyMap())).thenReturn(new KeywordIndex(List.of()));
        when(codeSampleIndexer.build(eq("main"), anyMap())).thenReturn(new CodeSampleIndex(List.of()));

        job.onStartup(null);

        // Verify the map passed to indexers has correct grouping
        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("quarkus-core", List.of("security.adoc"));
        expected.put("quarkus-openapi-generator", List.of(
                "quarkiverse/quarkus-openapi-generator/index.adoc",
                "quarkiverse/quarkus-openapi-generator/usage.adoc"));
        expected.put("quarkus-cxf", List.of("quarkiverse/quarkus-cxf/index.adoc"));

        verify(keywordIndexer).build(eq("main"), eq(expected));
    }
}
