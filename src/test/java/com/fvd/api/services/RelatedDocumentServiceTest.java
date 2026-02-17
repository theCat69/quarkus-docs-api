package com.fvd.api.services;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fvd.api.dto.RelatedDocumentResponse;
import com.fvd.docs.exceptions.DocNotFoundException;
import com.fvd.indexs.model.ChunkSearchRow;
import com.fvd.indexs.stores.DocChunkStore;
import com.fvd.search.SearchConfig;
import com.fvd.search.TestSearchConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelatedDocumentServiceTest {

    @Mock
    private DocChunkStore docChunkStore;

    private final SearchConfig searchConfig = new TestSearchConfig();

    private RelatedDocumentService service;

    @BeforeEach
    void setUp() {
        service = new RelatedDocumentService(docChunkStore, searchConfig);
    }

    @Test
    void shouldReturnRelatedDocumentsRankedBySimilarity() {
        // Source document with topics=[security, oidc] and extensions=[quarkus-core]
        ChunkSearchRow sourceChunk = new ChunkSearchRow(
                "security-overview#intro", "main", "security-overview", "Security Overview",
                "intro", null, List.of("security", "oidc"), List.of("quarkus-core"),
                "Overview of security", "content", 0.0);
        when(docChunkStore.findByPage("main", "security-overview")).thenReturn(List.of(sourceChunk));

        // Candidates: security-oidc shares 2 topics + 1 ext, config shares 0 topics + 1 ext
        DocChunkStore.RelatedPageRow securityOidc = new DocChunkStore.RelatedPageRow(
                "security-oidc", "Security OIDC", "OIDC guide",
                List.of("security", "oidc"), List.of("quarkus-core"), 0);
        DocChunkStore.RelatedPageRow config = new DocChunkStore.RelatedPageRow(
                "config", "Config", "Config doc",
                List.of("core-concepts"), List.of("quarkus-core"), 0);
        when(docChunkStore.findRelatedPages(eq("main"), eq("security-overview"),
                anyList(), anyList(), anyInt()))
                .thenReturn(List.of(securityOidc, config));

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "security-overview.adoc", null, null, 10);

        assertThat(response.getResults()).hasSize(2);
        // security-oidc shares 3 tags (security, oidc, quarkus-core) => higher score
        assertThat(response.getResults().get(0).path).isEqualTo("security-oidc.adoc");
        assertThat(response.getResults().get(0).similarityScore)
                .isGreaterThan(response.getResults().get(1).similarityScore);
    }

    @Test
    void shouldExcludeSourceDocumentViaQuery() {
        ChunkSearchRow sourceChunk = new ChunkSearchRow(
                "source#intro", "main", "source", "Source", "intro", null,
                List.of("test"), List.of("quarkus-core"), "Source doc", "content", 0.0);
        when(docChunkStore.findByPage("main", "source")).thenReturn(List.of(sourceChunk));

        DocChunkStore.RelatedPageRow other = new DocChunkStore.RelatedPageRow(
                "other", "Other", "Other doc",
                List.of("test"), List.of("quarkus-core"), 0);
        when(docChunkStore.findRelatedPages(eq("main"), eq("source"),
                anyList(), anyList(), anyInt()))
                .thenReturn(List.of(other));

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", null, null, 10);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).path).isEqualTo("other.adoc");
    }

    @Test
    void shouldFilterBySubject() {
        ChunkSearchRow sourceChunk = new ChunkSearchRow(
                "source#intro", "main", "source", "Source", "intro", null,
                List.of("security"), List.of("quarkus-core"), "Source doc", "content", 0.0);
        when(docChunkStore.findByPage("main", "source")).thenReturn(List.of(sourceChunk));

        DocChunkStore.RelatedPageRow securityDoc = new DocChunkStore.RelatedPageRow(
                "security-doc", "Security Doc", "Security details",
                List.of("security"), List.of("quarkus-core"), 0);
        DocChunkStore.RelatedPageRow configDoc = new DocChunkStore.RelatedPageRow(
                "config-doc", "Config Doc", "Config details",
                List.of("core-concepts"), List.of("quarkus-core"), 0);
        when(docChunkStore.findRelatedPages(eq("main"), eq("source"),
                anyList(), anyList(), anyInt()))
                .thenReturn(List.of(securityDoc, configDoc));

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", "security", null, 10);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).path).isEqualTo("security-doc.adoc");
    }

    @Test
    void shouldFilterByExtension() {
        ChunkSearchRow sourceChunk = new ChunkSearchRow(
                "source#intro", "main", "source", "Source", "intro", null,
                List.of("rest-apis"), List.of("quarkus-core"), "Source doc", "content", 0.0);
        when(docChunkStore.findByPage("main", "source")).thenReturn(List.of(sourceChunk));

        DocChunkStore.RelatedPageRow coreDoc = new DocChunkStore.RelatedPageRow(
                "core-doc", "Core Doc", "Core details",
                List.of("rest-apis"), List.of("quarkus-core"), 0);
        DocChunkStore.RelatedPageRow openapiDoc = new DocChunkStore.RelatedPageRow(
                "openapi-doc", "OpenAPI Doc", "OpenAPI details",
                List.of("rest-apis"), List.of("quarkus-openapi-generator"), 0);
        when(docChunkStore.findRelatedPages(eq("main"), eq("source"),
                anyList(), anyList(), anyInt()))
                .thenReturn(List.of(coreDoc, openapiDoc));

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", null, "quarkus-core", 10);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).path).isEqualTo("core-doc.adoc");
    }

    @Test
    void shouldRespectLimitParameter() {
        ChunkSearchRow sourceChunk = new ChunkSearchRow(
                "source#intro", "main", "source", "Source", "intro", null,
                List.of("test"), List.of("quarkus-core"), "Source doc", "content", 0.0);
        when(docChunkStore.findByPage("main", "source")).thenReturn(List.of(sourceChunk));

        DocChunkStore.RelatedPageRow doc1 = new DocChunkStore.RelatedPageRow(
                "doc1", "Doc1", "Summary1",
                List.of("test"), List.of("quarkus-core"), 0);
        DocChunkStore.RelatedPageRow doc2 = new DocChunkStore.RelatedPageRow(
                "doc2", "Doc2", "Summary2",
                List.of("test"), List.of("quarkus-core"), 0);
        DocChunkStore.RelatedPageRow doc3 = new DocChunkStore.RelatedPageRow(
                "doc3", "Doc3", "Summary3",
                List.of("test"), List.of("quarkus-core"), 0);
        when(docChunkStore.findRelatedPages(eq("main"), eq("source"),
                anyList(), anyList(), anyInt()))
                .thenReturn(List.of(doc1, doc2, doc3));

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", null, null, 2);

        assertThat(response.getResults()).hasSize(2);
        assertThat(response.getTotalCount()).isEqualTo(3);
    }

    @Test
    void shouldReturnEmptyWhenNoRelatedDocuments() {
        // Source has unique topics that no other doc shares
        ChunkSearchRow sourceChunk = new ChunkSearchRow(
                "source#intro", "main", "source", "Source", "intro", null,
                List.of("unique-topic"), List.of("unique-ext"), "Source doc", "content", 0.0);
        when(docChunkStore.findByPage("main", "source")).thenReturn(List.of(sourceChunk));

        when(docChunkStore.findRelatedPages(eq("main"), eq("source"),
                anyList(), anyList(), anyInt()))
                .thenReturn(List.of());

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", null, null, 10);

        assertThat(response.getResults()).isEmpty();
        assertThat(response.getTotalCount()).isEqualTo(0);
    }

    @Test
    void shouldThrowDocNotFoundWhenDocumentNotInIndex() {
        when(docChunkStore.findByPage("main", "nonexistent")).thenReturn(List.of());

        assertThatThrownBy(() -> service.findRelatedDocuments(
                "main", "nonexistent.adoc", null, null, 10))
                .isInstanceOf(DocNotFoundException.class)
                .hasMessageContaining("Document not found in index");
    }

    @Test
    void shouldThrowDocNotFoundWhenNoChunksForVersion() {
        when(docChunkStore.findByPage("missing", "any")).thenReturn(List.of());

        assertThatThrownBy(() -> service.findRelatedDocuments(
                "missing", "any.adoc", null, null, 10))
                .isInstanceOf(DocNotFoundException.class)
                .hasMessageContaining("Document not found in index");
    }

    @Test
    void shouldComputeOverlapScoreCorrectly() {
        // Source: topics=[security, oidc, auth], extensions=[quarkus-core]
        // Candidate: topics=[security, oidc], extensions=[quarkus-core]
        // Overlap = 2 shared topics + 1 shared extension = 3
        // totalSourceTags = 3 topics + 1 extension = 4
        // normalized = 3/4 = 0.75
        ChunkSearchRow sourceChunk = new ChunkSearchRow(
                "source#intro", "main", "source", "Source", "intro", null,
                List.of("security", "oidc", "auth"), List.of("quarkus-core"),
                "Source doc", "content", 0.0);
        when(docChunkStore.findByPage("main", "source")).thenReturn(List.of(sourceChunk));

        DocChunkStore.RelatedPageRow candidate = new DocChunkStore.RelatedPageRow(
                "candidate", "Candidate", "Summary",
                List.of("security", "oidc"), List.of("quarkus-core"), 0);
        when(docChunkStore.findRelatedPages(eq("main"), eq("source"),
                anyList(), anyList(), anyInt()))
                .thenReturn(List.of(candidate));

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", null, null, 10);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).similarityScore).isCloseTo(0.75, within(0.001));
    }

    @Test
    void shouldNormalizeSimilarityScore() {
        // Source: topics=[a, b], extensions=[]
        // Candidate: topics=[a], extensions=[]
        // Overlap = 1 shared topic, totalSourceTags = 2
        // normalized = 1/2 = 0.5
        ChunkSearchRow sourceChunk = new ChunkSearchRow(
                "source#intro", "main", "source", "Source", "intro", null,
                List.of("a", "b"), List.of(),
                "Source doc", "content", 0.0);
        when(docChunkStore.findByPage("main", "source")).thenReturn(List.of(sourceChunk));

        DocChunkStore.RelatedPageRow candidate = new DocChunkStore.RelatedPageRow(
                "candidate", "Candidate", "Summary",
                List.of("a"), List.of(), 0);
        when(docChunkStore.findRelatedPages(eq("main"), eq("source"),
                anyList(), anyList(), anyInt()))
                .thenReturn(List.of(candidate));

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", null, null, 10);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).similarityScore).isCloseTo(0.5, within(0.001));
    }

    @Test
    void shouldIncludeSharedTopicsAsSharedKeywords() {
        ChunkSearchRow sourceChunk = new ChunkSearchRow(
                "source#intro", "main", "source", "Source", "intro", null,
                List.of("security", "oidc", "auth"), List.of("quarkus-core"),
                "Source doc", "content", 0.0);
        when(docChunkStore.findByPage("main", "source")).thenReturn(List.of(sourceChunk));

        DocChunkStore.RelatedPageRow candidate = new DocChunkStore.RelatedPageRow(
                "candidate", "Candidate", "Summary",
                List.of("security", "oidc", "unrelated"), List.of("quarkus-core"), 0);
        when(docChunkStore.findRelatedPages(eq("main"), eq("source"),
                anyList(), anyList(), anyInt()))
                .thenReturn(List.of(candidate));

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", null, null, 10);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).sharedKeywords)
                .contains("security", "oidc")
                .doesNotContain("unrelated", "auth");
    }

    @Test
    void shouldReturnEmptyResponseWhenSourceHasNoTopicsOrExtensions() {
        ChunkSearchRow sourceChunk = new ChunkSearchRow(
                "source#intro", "main", "source", "Source", "intro", null,
                List.of(), List.of(), "Source doc", "content", 0.0);
        when(docChunkStore.findByPage("main", "source")).thenReturn(List.of(sourceChunk));

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", null, null, 10);

        assertThat(response.getResults()).isEmpty();
        assertThat(response.getTotalCount()).isEqualTo(0);
    }

    @Test
    void shouldEnrichResultsWithTitleAndDescription() {
        ChunkSearchRow sourceChunk = new ChunkSearchRow(
                "source#intro", "main", "source", "Source", "intro", null,
                List.of("security"), List.of("quarkus-core"), "Source doc", "content", 0.0);
        when(docChunkStore.findByPage("main", "source")).thenReturn(List.of(sourceChunk));

        DocChunkStore.RelatedPageRow candidate = new DocChunkStore.RelatedPageRow(
                "related", "Security Overview", "An overview of Quarkus security features",
                List.of("security"), List.of("quarkus-core"), 0);
        when(docChunkStore.findRelatedPages(eq("main"), eq("source"),
                anyList(), anyList(), anyInt()))
                .thenReturn(List.of(candidate));

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", null, null, 10);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).title).isEqualTo("Security Overview");
        assertThat(response.getResults().get(0).description).isEqualTo("An overview of Quarkus security features");
    }
}
