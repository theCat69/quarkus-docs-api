package com.fvd.api.services;

import com.fvd.api.dto.DocumentResponse;
import com.fvd.api.dto.DocumentSearchResponse;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.search.services.FileSearchResult;
import com.fvd.search.services.MatchedKeyword;
import com.fvd.search.services.PaginatedResult;
import com.fvd.search.services.SearchService;
import com.fvd.subject.services.MetadataAwareSubjectResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocStore docStore;

    @Mock
    private DocParser docParser;

    @Mock
    private KeywordIndexStore keywordIndexStore;

    @Mock
    private SearchService searchService;

    @Mock
    private MetadataAwareSubjectResolver metadataResolver;

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
        documentService = new DocumentService(docStore, docParser, keywordIndexStore, searchService, metadataResolver);
        documentService.documentCacheEnabled = true;
    }

    @Test
    void getDocumentByPathCachesOnFirstCallAndReturnsCachedOnSecond() {
        when(docStore.read("main", "security-overview.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(metadataResolver.resolveSubject(eq("main"), eq("security-overview.adoc"))).thenReturn("security");
        when(keywordIndexStore.read("main")).thenReturn(Optional.empty());
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
        when(docStore.read(eq("main"), eq("doc1.adoc"))).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(docStore.read(eq("3.27"), eq("doc1.adoc"))).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(metadataResolver.resolveSubject(anyString(), eq("doc1.adoc"))).thenReturn("general");
        when(keywordIndexStore.read(anyString())).thenReturn(Optional.empty());
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

        when(docStore.read("main", "doc.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(metadataResolver.resolveSubject(eq("main"), eq("doc.adoc"))).thenReturn("general");
        when(keywordIndexStore.read("main")).thenReturn(Optional.empty());
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
        List<MatchedKeyword> matchedKeywords = List.of(
                new MatchedKeyword("security", "security", "body", 5.0));
        FileSearchResult fileResult = new FileSearchResult(
                "security-overview.adoc", 10.0, matchedKeywords, "quarkus-core");
        PaginatedResult<FileSearchResult> searchResult = new PaginatedResult<>(List.of(fileResult), 1);

        when(searchService.searchFiles("main", List.of("security"), null, null, 5, 0))
                .thenReturn(searchResult);
        when(docStore.read("main", "security-overview.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(metadataResolver.loadMetadataMap("main")).thenReturn(Map.of());
        when(metadataResolver.resolveSubject(eq("security-overview.adoc"), any(Map.class))).thenReturn("security");
        when(metadataResolver.resolveSubject(eq("main"), eq("security-overview.adoc"))).thenReturn("security");
        when(keywordIndexStore.read("main")).thenReturn(Optional.empty());
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

        // docStore.read called twice for brief check in searchDocuments (once per call),
        // but getOrParseDocument only reads docStore on the first call
        // In searchDocuments, docStore.read is called for the brief/non-brief branch check,
        // then getOrParseDocument is called which also reads docStore on first call.
        // Actually: searchDocuments reads docStore first (for brief check), then getOrParseDocument
        // reads it again if not cached. On second call, searchDocuments reads docStore but
        // getOrParseDocument finds it cached.
        // So: 2 (from searchDocuments) + 1 (from getOrParseDocument first call) = 3
        // Wait - let me re-read the code. The non-brief branch calls getOrParseDocument directly,
        // but docStore.read is called before the brief check for the contentOpt check.
        // First call: docStore.read(1) + getOrParseDocument->docStore.read(2) = 2 reads
        // Second call: docStore.read(3) + getOrParseDocument returns cached = 3 reads total
        verify(docStore, times(3)).read("main", "security-overview.adoc");
    }

    @Test
    void searchDocumentsBriefModeDoesNotPopulateCache() {
        List<MatchedKeyword> matchedKeywords = List.of(
                new MatchedKeyword("security", "security", "body", 5.0));
        FileSearchResult fileResult = new FileSearchResult(
                "security-overview.adoc", 10.0, matchedKeywords, "quarkus-core");
        PaginatedResult<FileSearchResult> searchResult = new PaginatedResult<>(List.of(fileResult), 1);

        when(searchService.searchFiles("main", List.of("security"), null, null, 10, 0))
                .thenReturn(searchResult);
        when(docStore.read("main", "security-overview.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(metadataResolver.loadMetadataMap("main")).thenReturn(Map.of());
        when(metadataResolver.resolveSubject(eq("security-overview.adoc"), any(Map.class))).thenReturn("security");

        // Brief mode search
        DocumentSearchResponse response = documentService.searchDocuments(
                "main", List.of("security"), null, null, 10, 0, true);
        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).sections).isNull();
        assertThat(response.getResults().get(0).codeBlocks).isNull();

        // Now do a getDocumentByPath - cache should NOT have been populated by brief mode
        when(keywordIndexStore.read("main")).thenReturn(Optional.empty());
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of());
        when(docParser.parseCodeBlocks(SAMPLE_CONTENT)).thenReturn(List.of());

        documentService.getDocumentByPath("main", "security-overview.adoc");

        // docStore.read called: 1 (brief search) + 1 (getDocumentByPath via getOrParseDocument) = 2
        verify(docStore, times(2)).read("main", "security-overview.adoc");
        // parseSections should be called (cache was not populated by brief mode)
        verify(docParser, times(1)).parseSections(SAMPLE_CONTENT);
    }

    @Test
    void searchDocumentsNonBriefCapsLimitAtFullContentMaxLimit() {
        List<MatchedKeyword> matchedKeywords = List.of(
                new MatchedKeyword("security", "security", "body", 5.0));
        FileSearchResult fileResult = new FileSearchResult(
                "security-overview.adoc", 10.0, matchedKeywords, "quarkus-core");
        PaginatedResult<FileSearchResult> searchResult = new PaginatedResult<>(List.of(fileResult), 1);

        // When brief=false and limit=20, effective limit should be capped at 5
        when(searchService.searchFiles("main", List.of("security"), null, null, 5, 0))
                .thenReturn(searchResult);
        when(docStore.read("main", "security-overview.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(metadataResolver.loadMetadataMap("main")).thenReturn(Map.of());
        when(metadataResolver.resolveSubject(eq("security-overview.adoc"), any(Map.class))).thenReturn("security");
        when(metadataResolver.resolveSubject(eq("main"), eq("security-overview.adoc"))).thenReturn("security");
        when(keywordIndexStore.read("main")).thenReturn(Optional.empty());
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of());
        when(docParser.parseCodeBlocks(SAMPLE_CONTENT)).thenReturn(List.of());

        DocumentSearchResponse response = documentService.searchDocuments(
                "main", List.of("security"), null, null, 20, 0, false);
        assertThat(response.getResults()).hasSize(1);

        // Verify searchService was called with effective limit of 5 (not 20)
        verify(searchService).searchFiles("main", List.of("security"), null, null, 5, 0);
        verify(searchService, never()).searchFiles("main", List.of("security"), null, null, 20, 0);
    }

    @Test
    void searchDocumentsBriefModeUsesOriginalLimit() {
        List<MatchedKeyword> matchedKeywords = List.of(
                new MatchedKeyword("security", "security", "body", 5.0));
        FileSearchResult fileResult = new FileSearchResult(
                "security-overview.adoc", 10.0, matchedKeywords, "quarkus-core");
        PaginatedResult<FileSearchResult> searchResult = new PaginatedResult<>(List.of(fileResult), 1);

        // When brief=true and limit=20, effective limit should remain 20
        when(searchService.searchFiles("main", List.of("security"), null, null, 20, 0))
                .thenReturn(searchResult);
        when(docStore.read("main", "security-overview.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(metadataResolver.loadMetadataMap("main")).thenReturn(Map.of());
        when(metadataResolver.resolveSubject(eq("security-overview.adoc"), any(Map.class))).thenReturn("security");

        DocumentSearchResponse response = documentService.searchDocuments(
                "main", List.of("security"), null, null, 20, 0, true);
        assertThat(response.getResults()).hasSize(1);

        // Verify searchService was called with original limit of 20
        verify(searchService).searchFiles("main", List.of("security"), null, null, 20, 0);
    }

    @Test
    void searchDocumentsNonBriefSetsWarningWhenTotalExceedsLimit() {
        List<MatchedKeyword> matchedKeywords = List.of(
                new MatchedKeyword("rest", "rest", "body", 5.0));
        FileSearchResult fileResult = new FileSearchResult(
                "rest.adoc", 10.0, matchedKeywords, "quarkus-core");
        // Total is 10, which exceeds FULL_CONTENT_MAX_LIMIT (5)
        PaginatedResult<FileSearchResult> searchResult = new PaginatedResult<>(List.of(fileResult), 10);

        when(searchService.searchFiles("main", List.of("rest"), null, null, 5, 0))
                .thenReturn(searchResult);
        when(docStore.read("main", "rest.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(metadataResolver.loadMetadataMap("main")).thenReturn(Map.of());
        when(metadataResolver.resolveSubject(eq("rest.adoc"), any(Map.class))).thenReturn("rest-apis");
        when(metadataResolver.resolveSubject(eq("main"), eq("rest.adoc"))).thenReturn("rest-apis");
        when(keywordIndexStore.read("main")).thenReturn(Optional.empty());
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
        List<MatchedKeyword> matchedKeywords = List.of(
                new MatchedKeyword("oidc", "oidc", "body", 5.0));
        FileSearchResult fileResult = new FileSearchResult(
                "oidc.adoc", 10.0, matchedKeywords, "quarkus-core");
        // Total is 3, which is below FULL_CONTENT_MAX_LIMIT (5)
        PaginatedResult<FileSearchResult> searchResult = new PaginatedResult<>(List.of(fileResult), 3);

        when(searchService.searchFiles("main", List.of("oidc"), null, null, 5, 0))
                .thenReturn(searchResult);
        when(docStore.read("main", "oidc.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(metadataResolver.loadMetadataMap("main")).thenReturn(Map.of());
        when(metadataResolver.resolveSubject(eq("oidc.adoc"), any(Map.class))).thenReturn("security");
        when(metadataResolver.resolveSubject(eq("main"), eq("oidc.adoc"))).thenReturn("security");
        when(keywordIndexStore.read("main")).thenReturn(Optional.empty());
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of());
        when(docParser.parseCodeBlocks(SAMPLE_CONTENT)).thenReturn(List.of());

        DocumentSearchResponse response = documentService.searchDocuments(
                "main", List.of("oidc"), null, null, 20, 0, false);

        assertThat(response.warning).isNull();
    }
}
