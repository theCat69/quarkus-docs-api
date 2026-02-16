package com.fvd.api.services;

import com.fvd.api.dto.RelatedDocumentResponse;
import com.fvd.docs.exceptions.DocNotFoundException;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.search.SearchConfig;
import com.fvd.search.TestSearchConfig;
import com.fvd.search.services.SearchService;
import com.fvd.subject.services.MetadataAwareSubjectResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelatedDocumentServiceTest {

    @Mock
    private SearchService searchService;

    @Mock
    private DocStore docStore;

    @Mock
    private MetadataAwareSubjectResolver metadataResolver;

    private final SearchConfig searchConfig = new TestSearchConfig();

    private RelatedDocumentService service;

    private static final String DOC_CONTENT_SECURITY = """
            = Security Overview
            :description: An overview of Quarkus security features
            
            This document covers security basics.
            """;

    private static final String DOC_CONTENT_OIDC = """
            = OpenID Connect
            :description: OIDC authentication guide
            
            Guide to using OIDC with Quarkus.
            """;

    @BeforeEach
    void setUp() {
        service = new RelatedDocumentService(searchService, docStore, metadataResolver, searchConfig);
    }

    @Test
    void shouldReturnRelatedDocumentsRankedBySimilarity() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security-overview.adoc",
                        List.of(new KeywordScore("secur", 10), new KeywordScore("oidc", 8),
                                new KeywordScore("authent", 5)),
                        List.of(), "quarkus-core"),
                new FileKeywordEntry("security-oidc.adoc",
                        List.of(new KeywordScore("secur", 10), new KeywordScore("oidc", 12),
                                new KeywordScore("authent", 7)),
                        List.of(), "quarkus-core"),
                new FileKeywordEntry("config.adoc",
                        List.of(new KeywordScore("secur", 2), new KeywordScore("config", 15)),
                        List.of(), "quarkus-core")
        ));

        when(searchService.getKeywordIndex("main")).thenReturn(index);
        when(metadataResolver.loadMetadataMap("main")).thenReturn(Map.of());
        when(metadataResolver.resolveSubject(anyString(), any(Map.class))).thenReturn("security");
        when(docStore.read(eq("main"), eq("security-oidc.adoc"))).thenReturn(Optional.of(DOC_CONTENT_OIDC));
        when(docStore.read(eq("main"), eq("config.adoc"))).thenReturn(Optional.of("= Config\nConfig doc."));

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "security-overview.adoc", null, null, 10);

        assertThat(response.getResults()).hasSize(2);
        // security-oidc.adoc should rank higher (more shared high-weight keywords)
        assertThat(response.getResults().get(0).path).isEqualTo("security-oidc.adoc");
        assertThat(response.getResults().get(0).similarityScore)
                .isGreaterThan(response.getResults().get(1).similarityScore);
    }

    @Test
    void shouldExcludeSourceDocumentFromResults() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("source.adoc",
                        List.of(new KeywordScore("test", 10)),
                        List.of(), "quarkus-core"),
                new FileKeywordEntry("other.adoc",
                        List.of(new KeywordScore("test", 10)),
                        List.of(), "quarkus-core")
        ));

        when(searchService.getKeywordIndex("main")).thenReturn(index);
        when(metadataResolver.loadMetadataMap("main")).thenReturn(Map.of());
        when(metadataResolver.resolveSubject(anyString(), any(Map.class))).thenReturn("misc");
        when(docStore.read(eq("main"), eq("other.adoc"))).thenReturn(Optional.of("= Other\nOther doc."));

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", null, null, 10);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).path).isEqualTo("other.adoc");
    }

    @Test
    void shouldFilterBySubject() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("source.adoc",
                        List.of(new KeywordScore("secur", 10)),
                        List.of(), "quarkus-core"),
                new FileKeywordEntry("security-doc.adoc",
                        List.of(new KeywordScore("secur", 10)),
                        List.of(), "quarkus-core"),
                new FileKeywordEntry("config-doc.adoc",
                        List.of(new KeywordScore("secur", 5)),
                        List.of(), "quarkus-core")
        ));

        when(searchService.getKeywordIndex("main")).thenReturn(index);
        when(metadataResolver.loadMetadataMap("main")).thenReturn(Map.of());
        when(metadataResolver.resolveSubject(eq("security-doc.adoc"), any(Map.class))).thenReturn("security");
        when(metadataResolver.resolveSubject(eq("config-doc.adoc"), any(Map.class))).thenReturn("core-concepts");
        when(docStore.read(eq("main"), eq("security-doc.adoc"))).thenReturn(Optional.of("= Security\nSec doc."));

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", "security", null, 10);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).path).isEqualTo("security-doc.adoc");
    }

    @Test
    void shouldFilterByExtension() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("source.adoc",
                        List.of(new KeywordScore("rest", 10)),
                        List.of(), "quarkus-core"),
                new FileKeywordEntry("core-doc.adoc",
                        List.of(new KeywordScore("rest", 8)),
                        List.of(), "quarkus-core"),
                new FileKeywordEntry("openapi-doc.adoc",
                        List.of(new KeywordScore("rest", 8)),
                        List.of(), "quarkus-openapi-generator")
        ));

        when(searchService.getKeywordIndex("main")).thenReturn(index);
        when(metadataResolver.loadMetadataMap("main")).thenReturn(Map.of());
        when(metadataResolver.resolveSubject(anyString(), any(Map.class))).thenReturn("rest-apis");
        when(docStore.read(eq("main"), eq("core-doc.adoc"))).thenReturn(Optional.of("= Core\nCore doc."));

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", null, "quarkus-core", 10);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).path).isEqualTo("core-doc.adoc");
    }

    @Test
    void shouldRespectLimitParameter() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("source.adoc",
                        List.of(new KeywordScore("kw", 10)),
                        List.of(), "quarkus-core"),
                new FileKeywordEntry("doc1.adoc",
                        List.of(new KeywordScore("kw", 9)),
                        List.of(), "quarkus-core"),
                new FileKeywordEntry("doc2.adoc",
                        List.of(new KeywordScore("kw", 8)),
                        List.of(), "quarkus-core"),
                new FileKeywordEntry("doc3.adoc",
                        List.of(new KeywordScore("kw", 7)),
                        List.of(), "quarkus-core")
        ));

        when(searchService.getKeywordIndex("main")).thenReturn(index);
        when(metadataResolver.loadMetadataMap("main")).thenReturn(Map.of());
        when(metadataResolver.resolveSubject(anyString(), any(Map.class))).thenReturn("misc");
        when(docStore.read(eq("main"), anyString())).thenReturn(Optional.of("= Doc\nContent."));

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", null, null, 2);

        assertThat(response.getResults()).hasSize(2);
        assertThat(response.getTotalCount()).isEqualTo(3);
    }

    @Test
    void shouldReturnEmptyWhenNoRelatedDocuments() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("source.adoc",
                        List.of(new KeywordScore("unique1", 10)),
                        List.of(), "quarkus-core"),
                new FileKeywordEntry("other.adoc",
                        List.of(new KeywordScore("unique2", 10)),
                        List.of(), "quarkus-core")
        ));

        when(searchService.getKeywordIndex("main")).thenReturn(index);
        when(metadataResolver.loadMetadataMap("main")).thenReturn(Map.of());
        when(metadataResolver.resolveSubject(anyString(), any(Map.class))).thenReturn("misc");

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", null, null, 10);

        assertThat(response.getResults()).isEmpty();
        assertThat(response.getTotalCount()).isEqualTo(0);
    }

    @Test
    void shouldThrowDocNotFoundWhenDocumentNotInIndex() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("existing.adoc",
                        List.of(new KeywordScore("kw", 10)),
                        List.of(), "quarkus-core")
        ));

        when(searchService.getKeywordIndex("main")).thenReturn(index);

        assertThatThrownBy(() -> service.findRelatedDocuments(
                "main", "nonexistent.adoc", null, null, 10))
                .isInstanceOf(DocNotFoundException.class)
                .hasMessageContaining("Document not found in index");
    }

    @Test
    void shouldThrowDocNotFoundWhenNoIndexForVersion() {
        when(searchService.getKeywordIndex("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.findRelatedDocuments(
                "missing", "any.adoc", null, null, 10))
                .isInstanceOf(DocNotFoundException.class)
                .hasMessageContaining("No keyword index available");
    }

    @Test
    void shouldComputeCorrectCosineSimilarityForIdenticalVectors() {
        Map<String, Double> vectorA = Map.of("kw1", 10.0, "kw2", 5.0);
        Map<String, Double> vectorB = Map.of("kw1", 10.0, "kw2", 5.0);

        double similarity = service.computeCosineSimilarity(vectorA, vectorB);

        assertThat(similarity).isCloseTo(1.0, within(0.001));
    }

    @Test
    void shouldComputeZeroSimilarityForOrthogonalVectors() {
        Map<String, Double> vectorA = Map.of("kw1", 10.0, "kw2", 5.0);
        Map<String, Double> vectorB = Map.of("kw3", 10.0, "kw4", 5.0);

        double similarity = service.computeCosineSimilarity(vectorA, vectorB);

        assertThat(similarity).isEqualTo(0.0);
    }

    @Test
    void shouldComputeCorrectCosineSimilarityForPartialOverlap() {
        Map<String, Double> vectorA = Map.of("kw1", 10.0, "kw2", 5.0);
        Map<String, Double> vectorB = Map.of("kw1", 10.0, "kw3", 5.0);

        double similarity = service.computeCosineSimilarity(vectorA, vectorB);

        // dot = 10*10 = 100; normA = sqrt(100+25) = sqrt(125); normB = sqrt(100+25) = sqrt(125)
        // similarity = 100 / 125 = 0.8
        assertThat(similarity).isCloseTo(0.8, within(0.001));
    }

    @Test
    void shouldReturnZeroSimilarityForEmptyVector() {
        Map<String, Double> vectorA = new HashMap<>();
        Map<String, Double> vectorB = Map.of("kw1", 10.0);

        double similarity = service.computeCosineSimilarity(vectorA, vectorB);

        assertThat(similarity).isEqualTo(0.0);
    }

    @Test
    void shouldExcludeDocumentsBelowMinSimilarity() {
        // One doc shares a keyword, but with very low score
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("source.adoc",
                        List.of(new KeywordScore("secur", 10), new KeywordScore("oidc", 8)),
                        List.of(), "quarkus-core"),
                new FileKeywordEntry("barely-related.adoc",
                        List.of(new KeywordScore("secur", 1), new KeywordScore("unrelated", 100)),
                        List.of(), "quarkus-core")
        ));

        when(searchService.getKeywordIndex("main")).thenReturn(index);
        when(metadataResolver.loadMetadataMap("main")).thenReturn(Map.of());
        when(metadataResolver.resolveSubject(anyString(), any(Map.class))).thenReturn("misc");

        // The similarity is very low because "barely-related" has a huge unrelated keyword
        // and only shares "secur" with low weight
        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", null, null, 10);

        // All results should have similarity >= minSimilarity (0.05)
        for (var ref : response.getResults()) {
            assertThat(ref.similarityScore).isGreaterThanOrEqualTo(searchConfig.related().minSimilarity());
        }
    }

    @Test
    void shouldIncludeSharedKeywordsCappedAtMax() {
        // Create a document with many shared keywords
        List<KeywordScore> sourceKeywords = new java.util.ArrayList<>();
        List<KeywordScore> candidateKeywords = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            sourceKeywords.add(new KeywordScore("kw" + i, 10 - i));
            candidateKeywords.add(new KeywordScore("kw" + i, 10 - i));
        }

        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("source.adoc", sourceKeywords, List.of(), "quarkus-core"),
                new FileKeywordEntry("candidate.adoc", candidateKeywords, List.of(), "quarkus-core")
        ));

        when(searchService.getKeywordIndex("main")).thenReturn(index);
        when(metadataResolver.loadMetadataMap("main")).thenReturn(Map.of());
        when(metadataResolver.resolveSubject(anyString(), any(Map.class))).thenReturn("misc");
        when(docStore.read(eq("main"), eq("candidate.adoc"))).thenReturn(Optional.of("= Candidate\nContent."));

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", null, null, 10);

        assertThat(response.getResults()).hasSize(1);
        // Shared keywords should be capped at maxSharedKeywords (10)
        assertThat(response.getResults().get(0).sharedKeywords).hasSize(10);
    }

    @Test
    void shouldExtractSharedKeywordsSortedByCombinedScore() {
        Map<String, Double> vectorA = Map.of("high", 10.0, "low", 1.0, "mid", 5.0);
        Map<String, Double> vectorB = Map.of("high", 8.0, "low", 2.0, "mid", 6.0);
        Map<String, String> originals = Map.of("high", "high", "low", "low", "mid", "mid");

        List<String> shared = service.extractSharedKeywords(vectorA, vectorB, 10, originals);

        assertThat(shared).containsExactly("high", "mid", "low");
    }

    @Test
    void shouldReturnOriginalFormsInSharedKeywords() {
        Map<String, Double> vectorA = Map.of("secur", 10.0, "configur", 5.0);
        Map<String, Double> vectorB = Map.of("secur", 8.0, "configur", 6.0);
        Map<String, String> originals = Map.of("secur", "security", "configur", "configuration");

        List<String> shared = service.extractSharedKeywords(vectorA, vectorB, 10, originals);

        assertThat(shared).containsExactly("security", "configuration");
    }

    @Test
    void shouldFallBackToStemmedFormWhenNoOriginal() {
        Map<String, Double> vectorA = Map.of("secur", 10.0, "oidc", 5.0);
        Map<String, Double> vectorB = Map.of("secur", 8.0, "oidc", 6.0);
        // No originals map entry for "oidc" — should fall back to stemmed form
        Map<String, String> originals = Map.of("secur", "security");

        List<String> shared = service.extractSharedKeywords(vectorA, vectorB, 10, originals);

        assertThat(shared).contains("security", "oidc");
    }

    @Test
    void shouldReturnOriginalFormsInSharedKeywordsViaFindRelated() {
        // Use KeywordScore with originalWord to verify end-to-end flow
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("source.adoc",
                        List.of(new KeywordScore("secur", "security", 10, "body", 1),
                                new KeywordScore("configur", "configuration", 8, "body", 1)),
                        List.of(), "quarkus-core"),
                new FileKeywordEntry("related.adoc",
                        List.of(new KeywordScore("secur", "security", 10, "body", 1),
                                new KeywordScore("configur", "configuration", 7, "body", 1)),
                        List.of(), "quarkus-core")
        ));

        when(searchService.getKeywordIndex("main")).thenReturn(index);
        when(metadataResolver.loadMetadataMap("main")).thenReturn(Map.of());
        when(metadataResolver.resolveSubject(anyString(), any(Map.class))).thenReturn("security");
        when(docStore.read("main", "related.adoc")).thenReturn(Optional.of(DOC_CONTENT_SECURITY));

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", null, null, 10);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).sharedKeywords)
                .contains("security", "configuration")
                .doesNotContain("secur", "configur");
    }

    @Test
    void shouldEnrichResultsWithTitleAndDescription() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("source.adoc",
                        List.of(new KeywordScore("secur", 10)),
                        List.of(), "quarkus-core"),
                new FileKeywordEntry("related.adoc",
                        List.of(new KeywordScore("secur", 10)),
                        List.of(), "quarkus-core")
        ));

        when(searchService.getKeywordIndex("main")).thenReturn(index);
        when(metadataResolver.loadMetadataMap("main")).thenReturn(Map.of());
        when(metadataResolver.resolveSubject(anyString(), any(Map.class))).thenReturn("security");
        when(docStore.read("main", "related.adoc")).thenReturn(Optional.of(DOC_CONTENT_SECURITY));

        RelatedDocumentResponse response = service.findRelatedDocuments(
                "main", "source.adoc", null, null, 10);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).title).isEqualTo("Security Overview");
        assertThat(response.getResults().get(0).description).isNotBlank();
    }
}
