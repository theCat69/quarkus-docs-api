package com.fvd.api.services;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fvd.cache.services.CacheService;
import com.fvd.indexs.stores.DocChunkStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogServiceTest {

    @TempDir
    Path tempDir;

    private DocChunkStore docChunkStore;
    private CacheService cacheService;
    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        docChunkStore = mock(DocChunkStore.class);
        cacheService = mock(CacheService.class);
        when(cacheService.versionDir("main")).thenReturn(tempDir.resolve("main"));
        when(cacheService.listCachedVersions()).thenReturn(List.of("main"));
        catalogService = new CatalogService(docChunkStore, cacheService);
    }

    @Test
    void extensionWithTitleFileGetsDescription() throws Exception {
        Path extDir = tempDir.resolve("main/docs/quarkiverse/quarkus-openapi-generator");
        Files.createDirectories(extDir);
        Files.writeString(extDir.resolve(".extension-title"), "Quarkus OpenAPI Generator");

        Map<String, Integer> extensionsMap = new LinkedHashMap<>();
        extensionsMap.put("quarkus-openapi-generator", 1);
        when(docChunkStore.findDistinctExtensionsWithDocCount("main")).thenReturn(extensionsMap);
        when(docChunkStore.findDistinctTopicsWithDocCount("main")).thenReturn(Map.of());

        var catalog = catalogService.getCatalog("main");

        assertThat(catalog.extensions).hasSize(1);
        assertThat(catalog.extensions.get(0).description).isEqualTo("Quarkus OpenAPI Generator");
    }

    @Test
    void extensionWithoutTitleFileGetsEmptyDescription() {
        Map<String, Integer> extensionsMap = new LinkedHashMap<>();
        extensionsMap.put("my-ext", 1);
        when(docChunkStore.findDistinctExtensionsWithDocCount("main")).thenReturn(extensionsMap);
        when(docChunkStore.findDistinctTopicsWithDocCount("main")).thenReturn(Map.of());

        var catalog = catalogService.getCatalog("main");

        assertThat(catalog.extensions).hasSize(1);
        assertThat(catalog.extensions.get(0).description).isEmpty();
    }

    @Test
    void quarkusCoreGetsHardcodedDescription() {
        Map<String, Integer> extensionsMap = new LinkedHashMap<>();
        extensionsMap.put("quarkus-core", 1);
        when(docChunkStore.findDistinctExtensionsWithDocCount("main")).thenReturn(extensionsMap);
        when(docChunkStore.findDistinctTopicsWithDocCount("main")).thenReturn(Map.of());

        var catalog = catalogService.getCatalog("main");

        assertThat(catalog.extensions).hasSize(1);
        assertThat(catalog.extensions.get(0).name).isEqualTo("quarkus-core");
        assertThat(catalog.extensions.get(0).description).isEqualTo("Core Quarkus framework documentation");
    }

    @Test
    void extensionKeywordsAreAlwaysEmptyInNewModel() {
        Map<String, Integer> extensionsMap = new LinkedHashMap<>();
        extensionsMap.put("my-ext", 2);
        when(docChunkStore.findDistinctExtensionsWithDocCount("main")).thenReturn(extensionsMap);
        when(docChunkStore.findDistinctTopicsWithDocCount("main")).thenReturn(Map.of());

        var catalog = catalogService.getCatalog("main");

        assertThat(catalog.extensions).hasSize(1);
        assertThat(catalog.extensions.get(0).keywords).isEmpty();
    }

    @Test
    void extensionWithNoKeywordsGetsEmptyList() {
        Map<String, Integer> extensionsMap = new LinkedHashMap<>();
        extensionsMap.put("my-ext", 1);
        when(docChunkStore.findDistinctExtensionsWithDocCount("main")).thenReturn(extensionsMap);
        when(docChunkStore.findDistinctTopicsWithDocCount("main")).thenReturn(Map.of());

        var catalog = catalogService.getCatalog("main");

        assertThat(catalog.extensions).hasSize(1);
        assertThat(catalog.extensions.get(0).keywords).isEmpty();
    }

    @Test
    void multipleExtensionsAreSortedByDocCountThenName() {
        Map<String, Integer> extensionsMap = new LinkedHashMap<>();
        extensionsMap.put("ext-a", 1);
        extensionsMap.put("ext-b", 3);
        extensionsMap.put("ext-c", 1);
        when(docChunkStore.findDistinctExtensionsWithDocCount("main")).thenReturn(extensionsMap);
        when(docChunkStore.findDistinctTopicsWithDocCount("main")).thenReturn(Map.of());

        var catalog = catalogService.getCatalog("main");

        assertThat(catalog.extensions).hasSize(3);
        // ext-b (3 docs) first, then ext-a and ext-c (1 doc each, alphabetical)
        assertThat(catalog.extensions.get(0).name).isEqualTo("ext-b");
        assertThat(catalog.extensions.get(1).name).isEqualTo("ext-a");
        assertThat(catalog.extensions.get(2).name).isEqualTo("ext-c");
    }

    @Test
    void topicsAreReturnedAsSubjects() {
        Map<String, Integer> topicsMap = new LinkedHashMap<>();
        topicsMap.put("security", 5);
        topicsMap.put("rest-apis", 3);
        when(docChunkStore.findDistinctTopicsWithDocCount("main")).thenReturn(topicsMap);
        when(docChunkStore.findDistinctExtensionsWithDocCount("main")).thenReturn(Map.of());

        var catalog = catalogService.getCatalog("main");

        assertThat(catalog.subjects).hasSize(2);
        assertThat(catalog.subjects.get(0).name).isEqualTo("security");
        assertThat(catalog.subjects.get(0).displayName).isEqualTo("Security");
        assertThat(catalog.subjects.get(0).docCount).isEqualTo(5);
        assertThat(catalog.subjects.get(0).keywords).isEmpty();
        assertThat(catalog.subjects.get(1).name).isEqualTo("rest-apis");
        assertThat(catalog.subjects.get(1).displayName).isEqualTo("Rest Apis");
        assertThat(catalog.subjects.get(1).docCount).isEqualTo(3);
    }

    @Test
    void catalogCachesAndInvalidatesCorrectly() {
        Map<String, Integer> topicsMap = new LinkedHashMap<>();
        topicsMap.put("security", 1);
        when(docChunkStore.findDistinctTopicsWithDocCount("main")).thenReturn(topicsMap);
        when(docChunkStore.findDistinctExtensionsWithDocCount("main")).thenReturn(Map.of());

        // First call builds catalog
        var catalog1 = catalogService.getCatalog("main");
        assertThat(catalog1.subjects).hasSize(1);

        // Second call should return cached result
        var catalog2 = catalogService.getCatalog("main");
        assertThat(catalog2).isSameAs(catalog1);

        // DocChunkStore should only be called once (cached second time)
        verify(docChunkStore, times(1)).findDistinctTopicsWithDocCount("main");

        // Invalidate and re-fetch
        catalogService.invalidateCache("main");
        var catalog3 = catalogService.getCatalog("main");

        // Should have called DocChunkStore again after invalidation
        verify(docChunkStore, times(2)).findDistinctTopicsWithDocCount("main");
        assertThat(catalog3).isNotSameAs(catalog1);
    }
}
