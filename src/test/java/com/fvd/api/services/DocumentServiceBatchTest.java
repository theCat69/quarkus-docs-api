package com.fvd.api.services;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fvd.api.dto.BatchDocumentResponse;
import com.fvd.api.dto.DocumentResponse;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.model.ChunkSearchRow;
import com.fvd.indexs.stores.DocChunkStore;
import com.fvd.search.services.DocChunkSearchService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceBatchTest {

    @Mock
    private DocStore docStore;

    @Mock
    private DocParser docParser;

    @Mock
    private DocChunkSearchService docChunkSearchService;

    @Mock
    private DocChunkStore docChunkStore;

    @InjectMocks
    private DocumentService documentService;

    private static final String VERSION = "3.27";

    private static final String SECURITY_CONTENT = """
            = Security Guide
            :description: Introduction to security features.
            
            == Overview
            This is the overview section.
            """;

    private static final String CONFIG_CONTENT = """
            = Config Guide
            :description: Configuration reference.
            
            == Settings
            Config details here.
            """;

    private static final ChunkSearchRow SECURITY_CHUNK = new ChunkSearchRow(
            "security#overview", "3.27", "security", "Security Guide", "Overview",
            "https://quarkus.io/guides/security#overview",
            List.of("security"), List.of("quarkus-core"),
            "Introduction to security features.", "This is the overview section.", 0.0);

    private static final ChunkSearchRow CONFIG_CHUNK = new ChunkSearchRow(
            "config#settings", "3.27", "config", "Config Guide", "Settings",
            "https://quarkus.io/guides/config#settings",
            List.of("core-concepts"), List.of("quarkus-core"),
            "Configuration reference.", "Config details here.", 0.0);

    @Test
    void shouldReturnAllDocumentsWhenAllFound() {
        when(docStore.read(VERSION, "security.adoc")).thenReturn(Optional.of(SECURITY_CONTENT));
        when(docStore.read(VERSION, "config.adoc")).thenReturn(Optional.of(CONFIG_CONTENT));
        when(docChunkStore.findByPage(VERSION, "security")).thenReturn(List.of(SECURITY_CHUNK));
        when(docChunkStore.findByPage(VERSION, "config")).thenReturn(List.of(CONFIG_CHUNK));
        when(docParser.parseSections(SECURITY_CONTENT)).thenReturn(List.of());
        when(docParser.parseCodeBlocks(SECURITY_CONTENT)).thenReturn(List.of());
        when(docParser.parseSections(CONFIG_CONTENT)).thenReturn(List.of());
        when(docParser.parseCodeBlocks(CONFIG_CONTENT)).thenReturn(List.of());

        BatchDocumentResponse response = documentService.getDocumentsBatch(VERSION,
                List.of("security.adoc", "config.adoc"), false);

        assertThat(response.documents).hasSize(2);
        assertThat(response.errors).isEmpty();
        assertThat(response.requestedCount).isEqualTo(2);
        assertThat(response.retrievedCount).isEqualTo(2);
        assertThat(response.errorCount).isZero();
    }

    @Test
    void shouldReturnPartialResultsWhenSomeMissing() {
        when(docStore.read(VERSION, "security.adoc")).thenReturn(Optional.of(SECURITY_CONTENT));
        when(docStore.read(VERSION, "nonexistent.adoc")).thenReturn(Optional.empty());
        when(docChunkStore.findByPage(VERSION, "security")).thenReturn(List.of(SECURITY_CHUNK));
        when(docParser.parseSections(SECURITY_CONTENT)).thenReturn(List.of());
        when(docParser.parseCodeBlocks(SECURITY_CONTENT)).thenReturn(List.of());

        BatchDocumentResponse response = documentService.getDocumentsBatch(VERSION,
                List.of("security.adoc", "nonexistent.adoc"), false);

        assertThat(response.documents).hasSize(1);
        assertThat(response.documents.get(0).title).isEqualTo("Security Guide");
        assertThat(response.errors).hasSize(1);
        assertThat(response.errors.get(0).path).isEqualTo("nonexistent.adoc");
        assertThat(response.errors.get(0).reason).isEqualTo("Document not found");
        assertThat(response.requestedCount).isEqualTo(2);
        assertThat(response.retrievedCount).isEqualTo(1);
        assertThat(response.errorCount).isEqualTo(1);
    }

    @Test
    void shouldReturnAllErrorsWhenNoneFound() {
        when(docStore.read(VERSION, "missing1.adoc")).thenReturn(Optional.empty());
        when(docStore.read(VERSION, "missing2.adoc")).thenReturn(Optional.empty());

        BatchDocumentResponse response = documentService.getDocumentsBatch(VERSION,
                List.of("missing1.adoc", "missing2.adoc"), false);

        assertThat(response.documents).isEmpty();
        assertThat(response.errors).hasSize(2);
        assertThat(response.requestedCount).isEqualTo(2);
        assertThat(response.retrievedCount).isZero();
        assertThat(response.errorCount).isEqualTo(2);
    }

    @Test
    void shouldReturnBriefDocumentsWithoutSectionsAndCodeBlocks() {
        when(docStore.read(VERSION, "security.adoc")).thenReturn(Optional.of(SECURITY_CONTENT));
        when(docChunkStore.findByPage(VERSION, "security")).thenReturn(List.of(SECURITY_CHUNK));

        BatchDocumentResponse response = documentService.getDocumentsBatch(VERSION,
                List.of("security.adoc"), true);

        assertThat(response.documents).hasSize(1);
        DocumentResponse doc = response.documents.get(0);
        assertThat(doc.title).isEqualTo("Security Guide");
        assertThat(doc.description).isNotBlank();
        assertThat(doc.sections).isNull();
        assertThat(doc.codeBlocks).isNull();
    }

    @Test
    void shouldReturnFullDocumentsWithSectionsAndCodeBlocks() {
        when(docStore.read(VERSION, "security.adoc")).thenReturn(Optional.of(SECURITY_CONTENT));
        when(docChunkStore.findByPage(VERSION, "security")).thenReturn(List.of(SECURITY_CHUNK));
        when(docParser.parseSections(SECURITY_CONTENT)).thenReturn(List.of());
        when(docParser.parseCodeBlocks(SECURITY_CONTENT)).thenReturn(List.of());

        BatchDocumentResponse response = documentService.getDocumentsBatch(VERSION,
                List.of("security.adoc"), false);

        assertThat(response.documents).hasSize(1);
        DocumentResponse doc = response.documents.get(0);
        assertThat(doc.title).isEqualTo("Security Guide");
        assertThat(doc.sections).isNotNull();
        assertThat(doc.codeBlocks).isNotNull();
    }
}
