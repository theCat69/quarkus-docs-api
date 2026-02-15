package com.fvd.api.services;

import com.fvd.api.dto.BatchDocumentResponse;
import com.fvd.api.dto.DocumentResponse;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.search.services.SearchService;
import com.fvd.subject.services.SubjectDeriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceBatchTest {

    @Mock
    private DocStore docStore;

    @Mock
    private DocParser docParser;

    @Mock
    private KeywordIndexStore keywordIndexStore;

    @Mock
    private SearchService searchService;

    @Mock
    private SubjectDeriver subjectDeriver;

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

    @Test
    void shouldReturnAllDocumentsWhenAllFound() {
        when(docStore.read(VERSION, "security.adoc")).thenReturn(Optional.of(SECURITY_CONTENT));
        when(docStore.read(VERSION, "config.adoc")).thenReturn(Optional.of(CONFIG_CONTENT));
        when(subjectDeriver.deriveSubject("security.adoc")).thenReturn("security");
        when(subjectDeriver.deriveSubject("config.adoc")).thenReturn("core-concepts");
        when(keywordIndexStore.read(VERSION)).thenReturn(Optional.empty());
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
        when(subjectDeriver.deriveSubject("security.adoc")).thenReturn("security");
        when(keywordIndexStore.read(VERSION)).thenReturn(Optional.empty());
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
        when(subjectDeriver.deriveSubject("security.adoc")).thenReturn("security");
        when(keywordIndexStore.read(VERSION)).thenReturn(Optional.empty());

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
        when(subjectDeriver.deriveSubject("security.adoc")).thenReturn("security");
        when(keywordIndexStore.read(VERSION)).thenReturn(Optional.empty());
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
