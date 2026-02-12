package com.fvd.api.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaginatedResponseTest {

    @Test
    void ofFactoryMethodSetsAllFields() {
        List<String> items = List.of("a", "b", "c");

        PaginatedResponse<String> response = PaginatedResponse.of(items, 10);

        assertThat(response.getResults()).containsExactly("a", "b", "c");
        assertThat(response.getTotalCount()).isEqualTo(10);
        assertThat(response.getReturnedCount()).isEqualTo(3);
    }

    @Test
    void builderSetsAllFields() {
        PaginatedResponse<String> response = PaginatedResponse.<String>builder()
                .results(List.of("x", "y"))
                .totalCount(50)
                .returnedCount(2)
                .build();

        assertThat(response.getResults()).containsExactly("x", "y");
        assertThat(response.getTotalCount()).isEqualTo(50);
        assertThat(response.getReturnedCount()).isEqualTo(2);
    }

    @Test
    void ofFactoryMethodWithEmptyList() {
        List<String> items = List.of();

        PaginatedResponse<String> response = PaginatedResponse.of(items, 0);

        assertThat(response.getResults()).isEmpty();
        assertThat(response.getTotalCount()).isEqualTo(0);
        assertThat(response.getReturnedCount()).isEqualTo(0);
    }

    @Test
    void inheritanceWorksWithQuickSearchResponse() {
        SearchResultRef ref = new SearchResultRef(
                "path.adoc", "Title", "security", "quarkus-core",
                1.5, List.of("security"), "snippet");

        QuickSearchResponse response = QuickSearchResponse.builder()
                .results(List.of(ref))
                .totalCount(1)
                .returnedCount(1)
                .build();

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getReturnedCount()).isEqualTo(1);
        assertThat(response).isInstanceOf(PaginatedResponse.class);
    }

    @Test
    void inheritanceWorksWithCodeSampleSearchResponse() {
        CodeSampleSearchResponse response = CodeSampleSearchResponse.builder()
                .results(List.of())
                .totalCount(0)
                .returnedCount(0)
                .build();

        assertThat(response.getResults()).isEmpty();
        assertThat(response).isInstanceOf(PaginatedResponse.class);
    }

    @Test
    void inheritanceWorksWithDocumentSearchResponse() {
        DocumentSearchResponse response = DocumentSearchResponse.builder()
                .results(List.of())
                .totalCount(5)
                .returnedCount(0)
                .build();

        assertThat(response.getTotalCount()).isEqualTo(5);
        assertThat(response).isInstanceOf(PaginatedResponse.class);
    }
}
