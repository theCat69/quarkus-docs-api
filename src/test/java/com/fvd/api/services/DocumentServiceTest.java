package com.fvd.api.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fvd.api.dto.DocumentResponse;
import com.fvd.api.dto.DocumentSearchResponse;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.model.ChunkSearchRow;
import com.fvd.indexs.stores.DocChunkStore;
import com.fvd.search.services.ChunkSearchResult;
import com.fvd.search.services.DocChunkSearchService;
import com.fvd.search.services.PaginatedChunkResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocStore docStore;

    @Mock
    private DocParser docParser;

    @Mock
    private DocChunkSearchService docChunkSearchService;

    @Mock
    private DocChunkStore docChunkStore;

    private DocumentService documentService;

    private static final String SAMPLE_CONTENT = """
            = Security Overview
            :description: An overview of Quarkus security

            This is a sample document about security.

            == Authentication

            Authentication details here.

            == Authorization

            Authorization details here.
            """;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(docStore, docParser, docChunkSearchService, docChunkStore);
        documentService.documentCacheEnabled = true;
    }

    @Test
    void getDocumentByPathCachesOnFirstCallAndReturnsCachedOnSecond() {
        ChunkSearchRow chunkRow = new ChunkSearchRow(
                "security-overview#intro", "main", "security-overview", "Security Overview",
                "intro", "https://quarkus.io/guides/security-overview",
                List.of("security"), List.of("quarkus-core"), "Overview of security", "content", 0.0);
        when(docStore.read("main", "security-overview.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(docChunkStore.findByPage("main", "security-overview")).thenReturn(List.of(chunkRow));
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of(
                new DocParser.Section("Authentication", 7, 9, Map.of("authent", 1)),
                new DocParser.Section("Authorization", 11, 13, Map.of("author", 1))
        ));
        when(docParser.parseCodeBlocks(SAMPLE_CONTENT)).thenReturn(List.of());

        // First call - should parse
        DocumentResponse first = documentService.getDocumentByPath("main", "security-overview.adoc");
        assertThat(first).isNotNull();
        assertThat(first.title).isEqualTo("Security Overview");
        assertThat(first.sections).hasSize(2);
        assertThat(first.matchedKeywords).isEmpty();
        assertThat(first.score).isNull();

        // Second call - should use cache
        DocumentResponse second = documentService.getDocumentByPath("main", "security-overview.adoc");
        assertThat(second).isNotNull();
        assertThat(second.title).isEqualTo(first.title);
        assertThat(second.sections).hasSize(2);

        // docStore.read should be called only once (cached on second call)
        verify(docStore, times(1)).read("main", "security-overview.adoc");
    }

    @Test
    void invalidateDocumentCacheClearsOnlySpecifiedVersion() {
        ChunkSearchRow chunkRow = new ChunkSearchRow(
                "doc1#intro", "main", "doc1", "Doc1", "intro", null,
                List.of("general"), List.of(), "Summary", "content", 0.0);
        ChunkSearchRow chunkRow327 = new ChunkSearchRow(
                "doc1#intro", "3.27", "doc1", "Doc1", "intro", null,
                List.of("general"), List.of(), "Summary", "content", 0.0);

        when(docStore.read(eq("main"), eq("doc1.adoc"))).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(docStore.read(eq("3.27"), eq("doc1.adoc"))).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(docChunkStore.findByPage("main", "doc1")).thenReturn(List.of(chunkRow));
        when(docChunkStore.findByPage("3.27", "doc1")).thenReturn(List.of(chunkRow327));
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of());
        when(docParser.parseCodeBlocks(SAMPLE_CONTENT)).thenReturn(List.of());

        // Populate cache for both versions
        documentService.getDocumentByPath("main", "doc1.adoc");
        documentService.getDocumentByPath("3.27", "doc1.adoc");

        // Invalidate only "main"
        documentService.invalidateDocumentCache("main");

        // Fetch both again
        documentService.getDocumentByPath("main", "doc1.adoc");
        documentService.getDocumentByPath("3.27", "doc1.adoc");

        // "main" should have been read twice (original + after invalidation)
        verify(docStore, times(2)).read("main", "doc1.adoc");
        // "3.27" should have been read only once (still cached)
        verify(docStore, times(1)).read("3.27", "doc1.adoc");
    }

    @Test
    void cacheDisabledParsesEveryCall() {
        documentService.documentCacheEnabled = false;

        ChunkSearchRow chunkRow = new ChunkSearchRow(
                "doc#intro", "main", "doc", "Doc", "intro", null,
                List.of("general"), List.of(), "Summary", "content", 0.0);
        when(docStore.read("main", "doc.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(docChunkStore.findByPage("main", "doc")).thenReturn(List.of(chunkRow));
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of());
        when(docParser.parseCodeBlocks(SAMPLE_CONTENT)).thenReturn(List.of());

        documentService.getDocumentByPath("main", "doc.adoc");
        documentService.getDocumentByPath("main", "doc.adoc");
        documentService.getDocumentByPath("main", "doc.adoc");

        // Should read from docStore every time since cache is disabled
        verify(docStore, times(3)).read("main", "doc.adoc");
    }

    @Test
    void cacheMissWhenFileNotFoundReturnsNullAndDoesNotCache() {
        when(docStore.read("main", "missing.adoc")).thenReturn(Optional.empty());

        DocumentResponse first = documentService.getDocumentByPath("main", "missing.adoc");
        assertThat(first).isNull();

        DocumentResponse second = documentService.getDocumentByPath("main", "missing.adoc");
        assertThat(second).isNull();

        // Should read from docStore every time since null is not cached
        verify(docStore, times(2)).read("main", "missing.adoc");
    }

    @Test
    void searchDocumentsNonBriefModeUsesCache() {
        ChunkSearchResult chunkResult = new ChunkSearchResult(
                "security-overview#auth", "security-overview", "Security Overview", "Authentication",
                "Auth details", List.of("quarkus-core"), List.of("security"), 10.0,
                "https://quarkus.io/guides/security-overview#authentication");
        PaginatedChunkResult searchResult = new PaginatedChunkResult(List.of(chunkResult), 1, 5, 0);
        when(docChunkSearchService.search("security", "main", null, 5, 0)).thenReturn(searchResult);

        ChunkSearchRow chunkRow = new ChunkSearchRow(
                "security-overview#intro", "main", "security-overview", "Security Overview",
                "intro", null, List.of("security"), List.of("quarkus-core"), "Overview", "content", 0.0);
        when(docChunkStore.findByPage("main", "security-overview")).thenReturn(List.of(chunkRow));
        when(docStore.read("main", "security-overview.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of());
        when(docParser.parseCodeBlocks(SAMPLE_CONTENT)).thenReturn(List.of());

        // First search (non-brief) should parse and cache
        DocumentSearchResponse response1 = documentService.searchDocuments(
                "main", List.of("security"), null, null, 10, 0, false);
        assertThat(response1.getResults()).hasSize(1);

        // Second search (non-brief) should use cache
        DocumentSearchResponse response2 = documentService.searchDocuments(
                "main", List.of("security"), null, null, 10, 0, false);
        assertThat(response2.getResults()).hasSize(1);

        // docStore.read called once (getOrParseDocument first call), second call uses cache
        verify(docStore, times(1)).read("main", "security-overview.adoc");
    }

    @Test
    void searchDocumentsBriefModeDoesNotPopulateCache() {
        ChunkSearchResult chunkResult = new ChunkSearchResult(
                "security-overview#auth", "security-overview", "Security Overview", "Authentication",
                "Auth details", List.of("quarkus-core"), List.of("security"), 10.0,
                "https://quarkus.io/guides/security-overview#authentication");
        PaginatedChunkResult searchResult = new PaginatedChunkResult(List.of(chunkResult), 1, 10, 0);
        when(docChunkSearchService.search("security", "main", null, 10, 0)).thenReturn(searchResult);

        // Brief mode search
        DocumentSearchResponse response = documentService.searchDocuments(
                "main", List.of("security"), null, null, 10, 0, true);
        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).sections).isNull();
        assertThat(response.getResults().get(0).codeBlocks).isNull();

        // Now do a getDocumentByPath - cache should NOT have been populated by brief mode
        ChunkSearchRow chunkRow = new ChunkSearchRow(
                "security-overview#intro", "main", "security-overview", "Security Overview",
                "intro", null, List.of("security"), List.of("quarkus-core"), "Overview", "content", 0.0);
        when(docChunkStore.findByPage("main", "security-overview")).thenReturn(List.of(chunkRow));
        when(docStore.read("main", "security-overview.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of());
        when(docParser.parseCodeBlocks(SAMPLE_CONTENT)).thenReturn(List.of());

        documentService.getDocumentByPath("main", "security-overview.adoc");

        // docStore.read called once (from getDocumentByPath via getOrParseDocument)
        // Brief mode does not read from docStore
        verify(docStore, times(1)).read("main", "security-overview.adoc");
        // parseSections should be called (cache was not populated by brief mode)
        verify(docParser, times(1)).parseSections(SAMPLE_CONTENT);
    }

    @Test
    void searchDocumentsNonBriefCapsLimitAtFullContentMaxLimit() {
        ChunkSearchResult chunkResult = new ChunkSearchResult(
                "security-overview#auth", "security-overview", "Security Overview", "Authentication",
                "Auth details", List.of("quarkus-core"), List.of("security"), 10.0,
                "https://quarkus.io/guides/security-overview#authentication");
        PaginatedChunkResult searchResult = new PaginatedChunkResult(List.of(chunkResult), 1, 5, 0);

        // When brief=false and limit=20, effective limit should be capped at 5
        when(docChunkSearchService.search("security", "main", null, 5, 0)).thenReturn(searchResult);

        ChunkSearchRow chunkRow = new ChunkSearchRow(
                "security-overview#intro", "main", "security-overview", "Security Overview",
                "intro", null, List.of("security"), List.of("quarkus-core"), "Overview", "content", 0.0);
        when(docChunkStore.findByPage("main", "security-overview")).thenReturn(List.of(chunkRow));
        when(docStore.read("main", "security-overview.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of());
        when(docParser.parseCodeBlocks(SAMPLE_CONTENT)).thenReturn(List.of());

        DocumentSearchResponse response = documentService.searchDocuments(
                "main", List.of("security"), null, null, 20, 0, false);
        assertThat(response.getResults()).hasSize(1);

        // Verify docChunkSearchService was called with effective limit of 5 (not 20)
        verify(docChunkSearchService).search("security", "main", null, 5, 0);
        verify(docChunkSearchService, never()).search("security", "main", null, 20, 0);
    }

    @Test
    void searchDocumentsBriefModeUsesOriginalLimit() {
        ChunkSearchResult chunkResult = new ChunkSearchResult(
                "security-overview#auth", "security-overview", "Security Overview", "Authentication",
                "Auth details", List.of("quarkus-core"), List.of("security"), 10.0,
                "https://quarkus.io/guides/security-overview#authentication");
        PaginatedChunkResult searchResult = new PaginatedChunkResult(List.of(chunkResult), 1, 20, 0);

        // When brief=true and limit=20, effective limit should remain 20
        when(docChunkSearchService.search("security", "main", null, 20, 0)).thenReturn(searchResult);

        DocumentSearchResponse response = documentService.searchDocuments(
                "main", List.of("security"), null, null, 20, 0, true);
        assertThat(response.getResults()).hasSize(1);

        // Verify docChunkSearchService was called with original limit of 20
        verify(docChunkSearchService).search("security", "main", null, 20, 0);
    }

    @Test
    void searchDocumentsNonBriefSetsWarningWhenTotalExceedsLimit() {
        ChunkSearchResult chunkResult = new ChunkSearchResult(
                "rest#intro", "rest", "REST", "Intro",
                "REST details", List.of("quarkus-core"), List.of("rest-apis"), 10.0,
                "https://quarkus.io/guides/rest");
        // Total is 10, which exceeds FULL_CONTENT_MAX_LIMIT (5)
        PaginatedChunkResult searchResult = new PaginatedChunkResult(List.of(chunkResult), 10, 5, 0);

        when(docChunkSearchService.search("rest", "main", null, 5, 0)).thenReturn(searchResult);

        ChunkSearchRow chunkRow = new ChunkSearchRow(
                "rest#intro", "main", "rest", "REST", "intro", null,
                List.of("rest-apis"), List.of("quarkus-core"), "REST overview", "content", 0.0);
        when(docChunkStore.findByPage("main", "rest")).thenReturn(List.of(chunkRow));
        when(docStore.read("main", "rest.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of());
        when(docParser.parseCodeBlocks(SAMPLE_CONTENT)).thenReturn(List.of());

        DocumentSearchResponse response = documentService.searchDocuments(
                "main", List.of("rest"), null, null, 20, 0, false);

        assertThat(response.warning).isNotNull();
        assertThat(response.warning).contains("brief=false");
        assertThat(response.warning).contains("limited to 5");
    }

    @Test
    void searchDocumentsNonBriefNoWarningWhenTotalBelowLimit() {
        ChunkSearchResult chunkResult = new ChunkSearchResult(
                "oidc#intro", "oidc", "OIDC", "Intro",
                "OIDC details", List.of("quarkus-core"), List.of("security"), 10.0,
                "https://quarkus.io/guides/oidc");
        // Total is 3, which is below FULL_CONTENT_MAX_LIMIT (5)
        PaginatedChunkResult searchResult = new PaginatedChunkResult(List.of(chunkResult), 3, 5, 0);

        when(docChunkSearchService.search("oidc", "main", null, 5, 0)).thenReturn(searchResult);

        ChunkSearchRow chunkRow = new ChunkSearchRow(
                "oidc#intro", "main", "oidc", "OIDC", "intro", null,
                List.of("security"), List.of("quarkus-core"), "OIDC overview", "content", 0.0);
        when(docChunkStore.findByPage("main", "oidc")).thenReturn(List.of(chunkRow));
        when(docStore.read("main", "oidc.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of());
        when(docParser.parseCodeBlocks(SAMPLE_CONTENT)).thenReturn(List.of());

        DocumentSearchResponse response = documentService.searchDocuments(
                "main", List.of("oidc"), null, null, 20, 0, false);

        assertThat(response.warning).isNull();
    }
}
