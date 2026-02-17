package com.fvd.search.services;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fvd.common.utils.UrlBuilder;
import com.fvd.indexs.model.ChunkSearchRow;
import com.fvd.indexs.stores.DocChunkStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocChunkSearchServiceTest {

    @Mock
    private DocChunkStore docChunkStore;

    @Mock
    private UrlBuilder urlBuilder;

    private DocChunkSearchService searchService;

    private static final String VERSION = "main";
    private static final String QUERY = "reactive";

    @BeforeEach
    void setUp() {
        searchService = new DocChunkSearchService(docChunkStore, urlBuilder);
    }

    @Test
    void shouldReturnFtsResultsWhenFound() {
        ChunkSearchRow row = new ChunkSearchRow(
                "rest#reactive", VERSION, "rest", "REST Guide", "Reactive",
                "https://quarkus.io/guides/rest#reactive",
                List.of("rest"), List.of("quarkus-core"),
                "Reactive REST endpoints", "Content about reactive", 0.85);
        when(docChunkStore.search(QUERY, VERSION, null, 20, 0)).thenReturn(List.of(row));
        when(docChunkStore.countSearch(QUERY, VERSION, null)).thenReturn(1);

        PaginatedChunkResult result = searchService.search(QUERY, VERSION, null, 20, 0);

        assertThat(result.results()).hasSize(1);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.results().getFirst().id()).isEqualTo("rest#reactive");
        assertThat(result.results().getFirst().url()).isEqualTo("https://quarkus.io/guides/rest#reactive");
        verify(docChunkStore, never()).fuzzySearch(any(), any(), anyInt());
    }

    @Test
    void shouldFallbackToFuzzyWhenFtsEmptyAndOffsetZero() {
        when(docChunkStore.search(QUERY, VERSION, null, 20, 0)).thenReturn(List.of());

        ChunkSearchRow fuzzyRow = new ChunkSearchRow(
                "rest#intro", VERSION, "rest", "REST Guide", "Introduction",
                "https://quarkus.io/guides/rest#introduction",
                List.of("rest"), List.of("quarkus-core"),
                "Introduction to REST", "Content about reactive endpoints", 0.15);
        when(docChunkStore.fuzzySearch(QUERY, VERSION, 20)).thenReturn(List.of(fuzzyRow));

        PaginatedChunkResult result = searchService.search(QUERY, VERSION, null, 20, 0);

        assertThat(result.results()).hasSize(1);
        assertThat(result.total()).isEqualTo(1);
        verify(docChunkStore).fuzzySearch(QUERY, VERSION, 20);
        verify(docChunkStore, never()).countSearch(any(), any(), any());
    }

    @Test
    void shouldNotFallbackToFuzzyWhenOffsetNonZero() {
        when(docChunkStore.search(QUERY, VERSION, null, 20, 5)).thenReturn(List.of());
        when(docChunkStore.countSearch(QUERY, VERSION, null)).thenReturn(0);

        PaginatedChunkResult result = searchService.search(QUERY, VERSION, null, 20, 5);

        assertThat(result.results()).isEmpty();
        assertThat(result.total()).isEqualTo(0);
        verify(docChunkStore, never()).fuzzySearch(any(), any(), anyInt());
    }

    @Test
    void shouldReturnTotalFromCountSearch() {
        ChunkSearchRow row = new ChunkSearchRow(
                "security#overview", VERSION, "security", "Security", "Overview",
                "https://quarkus.io/guides/security#overview",
                List.of("security"), List.of("quarkus-core"),
                "Security overview", "Content about security", 0.9);
        when(docChunkStore.search(QUERY, VERSION, null, 20, 0)).thenReturn(List.of(row));
        when(docChunkStore.countSearch(QUERY, VERSION, null)).thenReturn(42);

        PaginatedChunkResult result = searchService.search(QUERY, VERSION, null, 20, 0);

        assertThat(result.total()).isEqualTo(42);
        verify(docChunkStore).countSearch(QUERY, VERSION, null);
    }

    @Test
    void shouldBuildUrlsCorrectly() {
        ChunkSearchRow rowWithUrl = new ChunkSearchRow(
                "rest#reactive", VERSION, "rest", "REST Guide", "Reactive",
                "https://quarkus.io/guides/rest#reactive",
                List.of("rest"), List.of("quarkus-core"),
                "Reactive REST", "Content", 0.85);
        when(docChunkStore.search(QUERY, VERSION, null, 20, 0)).thenReturn(List.of(rowWithUrl));
        when(docChunkStore.countSearch(QUERY, VERSION, null)).thenReturn(1);

        PaginatedChunkResult result = searchService.search(QUERY, VERSION, null, 20, 0);

        assertThat(result.results().getFirst().url()).isEqualTo("https://quarkus.io/guides/rest#reactive");
        verify(urlBuilder, never()).buildUrl(any(), any());
    }

    @Test
    void shouldBuildUrlWhenRowUrlIsNull() {
        ChunkSearchRow rowNoUrl = new ChunkSearchRow(
                "rest#reactive", VERSION, "rest", "REST Guide", "Reactive",
                null,
                List.of("rest"), List.of("quarkus-core"),
                "Reactive REST", "Content", 0.85);
        when(docChunkStore.search(QUERY, VERSION, null, 20, 0)).thenReturn(List.of(rowNoUrl));
        when(docChunkStore.countSearch(QUERY, VERSION, null)).thenReturn(1);
        when(urlBuilder.buildUrl("rest", "Reactive")).thenReturn("https://quarkus.io/guides/rest#reactive");

        PaginatedChunkResult result = searchService.search(QUERY, VERSION, null, 20, 0);

        assertThat(result.results().getFirst().url()).isEqualTo("https://quarkus.io/guides/rest#reactive");
        verify(urlBuilder).buildUrl("rest", "Reactive");
    }

    @Test
    void shouldHandleNullUrlGracefully() {
        ChunkSearchRow rowNoUrl = new ChunkSearchRow(
                "rest#reactive", VERSION, "rest", "REST Guide", "Reactive",
                null,
                List.of("rest"), List.of("quarkus-core"),
                "Reactive REST", "Content", 0.85);
        when(docChunkStore.search(QUERY, VERSION, null, 20, 0)).thenReturn(List.of(rowNoUrl));
        when(docChunkStore.countSearch(QUERY, VERSION, null)).thenReturn(1);
        when(urlBuilder.buildUrl("rest", "Reactive")).thenReturn(null);

        PaginatedChunkResult result = searchService.search(QUERY, VERSION, null, 20, 0);

        assertThat(result.results().getFirst().url()).isNull();
    }

    @Test
    void shouldSetLimitAndOffsetOnResult() {
        when(docChunkStore.search(QUERY, VERSION, null, 10, 5)).thenReturn(List.of());
        when(docChunkStore.countSearch(QUERY, VERSION, null)).thenReturn(0);

        PaginatedChunkResult result = searchService.search(QUERY, VERSION, null, 10, 5);

        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.offset()).isEqualTo(5);
    }
}
