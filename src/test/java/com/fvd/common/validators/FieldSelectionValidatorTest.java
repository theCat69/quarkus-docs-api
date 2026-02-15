package com.fvd.common.validators;

import com.fvd.api.dto.BatchDocumentResponse;
import com.fvd.api.dto.CatalogResponse;
import com.fvd.api.dto.CodeSampleResult;
import com.fvd.api.dto.CodeSampleSearchResponse;
import com.fvd.api.dto.DocumentResponse;
import com.fvd.api.dto.DocumentSearchResponse;
import com.fvd.api.dto.QuickSearchResponse;
import com.fvd.api.dto.RelatedDocumentRef;
import com.fvd.api.dto.RelatedDocumentResponse;
import com.fvd.api.dto.SearchResultRef;
import com.fvd.common.exceptions.InvalidInputException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldSelectionValidatorTest {

    // --- parseAndValidate: valid fields ---

    @Test
    void shouldReturnRequestedFieldsWhenAllValid() {
        SearchResultRef entity = new SearchResultRef();
        Set<String> result = FieldSelectionValidator.parseAndValidate("title,path", entity);
        assertThat(result).containsExactlyInAnyOrder("title", "path");
    }

    @Test
    void shouldReturnSingleFieldWhenValid() {
        SearchResultRef entity = new SearchResultRef();
        Set<String> result = FieldSelectionValidator.parseAndValidate("score", entity);
        assertThat(result).containsExactly("score");
    }

    @Test
    void shouldTrimWhitespaceFromFieldNames() {
        SearchResultRef entity = new SearchResultRef();
        Set<String> result = FieldSelectionValidator.parseAndValidate(" title , path ", entity);
        assertThat(result).containsExactlyInAnyOrder("title", "path");
    }

    @Test
    void shouldReturnEmptySetForBlankFields() {
        SearchResultRef entity = new SearchResultRef();
        Set<String> result = FieldSelectionValidator.parseAndValidate("  ,  , ", entity);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptySetForEmptyAfterTrim() {
        SearchResultRef entity = new SearchResultRef();
        Set<String> result = FieldSelectionValidator.parseAndValidate(",,,", entity);
        assertThat(result).isEmpty();
    }

    // --- parseAndValidate: invalid fields ---

    @Test
    void shouldThrowForInvalidFieldName() {
        SearchResultRef entity = new SearchResultRef();
        assertThatThrownBy(() -> FieldSelectionValidator.parseAndValidate("nonexistent", entity))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Unknown field(s): nonexistent")
                .hasMessageContaining("Available fields:");
    }

    @Test
    void shouldThrowForMixedValidAndInvalidFields() {
        SearchResultRef entity = new SearchResultRef();
        assertThatThrownBy(() -> FieldSelectionValidator.parseAndValidate("title,nonexistent,path", entity))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Unknown field(s): nonexistent")
                .hasMessageContaining("Available fields:")
                .hasMessageNotContaining("Unknown field(s): nonexistent, path")
                .hasMessageNotContaining("Unknown field(s): nonexistent, title")
                .hasMessageNotContaining("Unknown field(s): path")
                .hasMessageNotContaining("Unknown field(s): title");
    }

    @Test
    void shouldListAvailableFieldsInErrorMessage() {
        SearchResultRef entity = new SearchResultRef();
        assertThatThrownBy(() -> FieldSelectionValidator.parseAndValidate("invalid", entity))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("title")
                .hasMessageContaining("path")
                .hasMessageContaining("score");
    }

    @Test
    void shouldThrowForMultipleInvalidFields() {
        SearchResultRef entity = new SearchResultRef();
        assertThatThrownBy(() -> FieldSelectionValidator.parseAndValidate("bad1,bad2", entity))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("bad1")
                .hasMessageContaining("bad2");
    }

    // --- resolveItemClass: paginated responses ---

    @Test
    void shouldResolveSearchResultRefFromQuickSearchResponse() {
        QuickSearchResponse entity = QuickSearchResponse.builder().results(List.of()).totalCount(0).returnedCount(0).build();
        Class<?> resolved = FieldSelectionValidator.resolveItemClass(entity);
        assertThat(resolved).isEqualTo(SearchResultRef.class);
    }

    @Test
    void shouldResolveDocumentResponseFromDocumentSearchResponse() {
        DocumentSearchResponse entity = DocumentSearchResponse.builder().results(List.of()).totalCount(0).returnedCount(0).build();
        Class<?> resolved = FieldSelectionValidator.resolveItemClass(entity);
        assertThat(resolved).isEqualTo(DocumentResponse.class);
    }

    @Test
    void shouldResolveCodeSampleResultFromCodeSampleSearchResponse() {
        CodeSampleSearchResponse entity = CodeSampleSearchResponse.builder().results(List.of()).totalCount(0).returnedCount(0).build();
        Class<?> resolved = FieldSelectionValidator.resolveItemClass(entity);
        assertThat(resolved).isEqualTo(CodeSampleResult.class);
    }

    @Test
    void shouldResolveRelatedDocumentRefFromRelatedDocumentResponse() {
        RelatedDocumentResponse entity = RelatedDocumentResponse.builder().results(List.of()).totalCount(0).returnedCount(0).build();
        Class<?> resolved = FieldSelectionValidator.resolveItemClass(entity);
        assertThat(resolved).isEqualTo(RelatedDocumentRef.class);
    }

    @Test
    void shouldResolveBatchDocumentResponseToItself() {
        BatchDocumentResponse entity = new BatchDocumentResponse();
        Class<?> resolved = FieldSelectionValidator.resolveItemClass(entity);
        assertThat(resolved).isEqualTo(BatchDocumentResponse.class);
    }

    // --- resolveItemClass: direct DTOs ---

    @Test
    void shouldResolveCatalogResponseToItself() {
        CatalogResponse entity = new CatalogResponse();
        Class<?> resolved = FieldSelectionValidator.resolveItemClass(entity);
        assertThat(resolved).isEqualTo(CatalogResponse.class);
    }

    @Test
    void shouldResolveDocumentResponseToItself() {
        DocumentResponse entity = new DocumentResponse();
        Class<?> resolved = FieldSelectionValidator.resolveItemClass(entity);
        assertThat(resolved).isEqualTo(DocumentResponse.class);
    }

    // --- parseAndValidate with paginated wrapper ---

    @Test
    void shouldValidateFieldsAgainstItemTypeForPaginatedResponse() {
        QuickSearchResponse entity = QuickSearchResponse.builder().results(List.of()).totalCount(0).returnedCount(0).build();
        Set<String> result = FieldSelectionValidator.parseAndValidate("title,path,score", entity);
        assertThat(result).containsExactlyInAnyOrder("title", "path", "score");
    }

    @Test
    void shouldRejectFieldsNotOnItemTypeForPaginatedResponse() {
        QuickSearchResponse entity = QuickSearchResponse.builder().results(List.of()).totalCount(0).returnedCount(0).build();
        assertThatThrownBy(() -> FieldSelectionValidator.parseAndValidate("results", entity))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Unknown field(s): results");
    }

    // --- parseAndValidate with CatalogResponse ---

    @Test
    void shouldValidateCatalogFields() {
        CatalogResponse entity = new CatalogResponse();
        Set<String> result = FieldSelectionValidator.parseAndValidate("subjects,versions", entity);
        assertThat(result).containsExactlyInAnyOrder("subjects", "versions");
    }

    @Test
    void shouldRejectInvalidCatalogFields() {
        CatalogResponse entity = new CatalogResponse();
        assertThatThrownBy(() -> FieldSelectionValidator.parseAndValidate("nonexistent", entity))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Unknown field(s): nonexistent");
    }

    // --- getFieldNames ---

    @Test
    void shouldReturnAllPublicFieldNamesForSearchResultRef() {
        Set<String> fields = FieldSelectionValidator.getFieldNames(SearchResultRef.class);
        assertThat(fields).containsExactlyInAnyOrder(
                "path", "title", "subject", "extension", "score", "matchedKeywords", "snippet");
    }

    @Test
    void shouldReturnAllPublicFieldNamesForDocumentResponse() {
        Set<String> fields = FieldSelectionValidator.getFieldNames(DocumentResponse.class);
        assertThat(fields).containsExactlyInAnyOrder(
                "title", "description", "path", "subject", "extension",
                "sections", "codeBlocks", "matchedKeywords", "score");
    }

    @Test
    void shouldReturnAllPublicFieldNamesForCodeSampleResult() {
        Set<String> fields = FieldSelectionValidator.getFieldNames(CodeSampleResult.class);
        assertThat(fields).containsExactlyInAnyOrder(
                "language", "content", "context", "documentPath", "documentTitle",
                "subject", "extension", "matchedKeywords", "score", "startLine", "endLine");
    }

    @Test
    void shouldReturnAllPublicFieldNamesForCatalogResponse() {
        Set<String> fields = FieldSelectionValidator.getFieldNames(CatalogResponse.class);
        assertThat(fields).containsExactlyInAnyOrder("subjects", "extensions", "versions");
    }

    // --- edge case: no-op for empty entity ---

    @Test
    void shouldNotThrowForAllValidFieldsOnDocumentResponse() {
        DocumentResponse entity = new DocumentResponse();
        assertThatCode(() -> FieldSelectionValidator.parseAndValidate(
                "title,description,path,subject,extension,sections,codeBlocks,matchedKeywords,score", entity))
                .doesNotThrowAnyException();
    }
}
