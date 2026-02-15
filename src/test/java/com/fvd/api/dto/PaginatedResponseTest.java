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
        assertThat(response.getOffset()).isEqualTo(0);
        assertThat(response.getLimit()).isEqualTo(3);
        assertThat(response.isHasMore()).isTrue();
    }

    @Test
    void ofFactoryMethodWithOffsetAndLimit() {
        List<String> items = List.of("a", "b", "c");

        PaginatedResponse<String> response = PaginatedResponse.of(items, 42, 10, 5);

        assertThat(response.getResults()).containsExactly("a", "b", "c");
        assertThat(response.getTotalCount()).isEqualTo(42);
        assertThat(response.getReturnedCount()).isEqualTo(3);
        assertThat(response.getOffset()).isEqualTo(10);
        assertThat(response.getLimit()).isEqualTo(5);
        assertThat(response.isHasMore()).isTrue();
    }

    @Test
    void hasMoreIsTrueWhenMoreResultsExist() {
        PaginatedResponse<String> response = PaginatedResponse.of(
                List.of("a", "b", "c", "d", "e"), 42, 0, 5);

        assertThat(response.isHasMore()).isTrue();
    }

    @Test
    void hasMoreIsFalseOnLastPage() {
        List<String> items = List.of("a", "b");

        PaginatedResponse<String> response = PaginatedResponse.of(items, 42, 40, 5);

        assertThat(response.isHasMore()).isFalse();
    }

    @Test
    void hasMoreIsFalseOnExactBoundary() {
        List<String> items = List.of("a", "b", "c", "d", "e");

        PaginatedResponse<String> response = PaginatedResponse.of(items, 10, 5, 5);

        assertThat(response.isHasMore()).isFalse();
    }

    @Test
    void hasMoreIsFalseWhenOffsetBeyondTotal() {
        List<String> items = List.of();

        PaginatedResponse<String> response = PaginatedResponse.of(items, 42, 50, 5);

        assertThat(response.isHasMore()).isFalse();
    }

    @Test
    void builderSetsAllFields() {
        PaginatedResponse<String> response = PaginatedResponse.<String>builder()
                .results(List.of("x", "y"))
                .totalCount(50)
                .returnedCount(2)
                .offset(10)
                .limit(5)
                .hasMore(true)
                .build();

        assertThat(response.getResults()).containsExactly("x", "y");
        assertThat(response.getTotalCount()).isEqualTo(50);
        assertThat(response.getReturnedCount()).isEqualTo(2);
        assertThat(response.getOffset()).isEqualTo(10);
        assertThat(response.getLimit()).isEqualTo(5);
        assertThat(response.isHasMore()).isTrue();
    }

    @Test
    void ofFactoryMethodWithEmptyList() {
        List<String> items = List.of();

        PaginatedResponse<String> response = PaginatedResponse.of(items, 0);

        assertThat(response.getResults()).isEmpty();
        assertThat(response.getTotalCount()).isEqualTo(0);
        assertThat(response.getReturnedCount()).isEqualTo(0);
        assertThat(response.getOffset()).isEqualTo(0);
        assertThat(response.getLimit()).isEqualTo(0);
        assertThat(response.isHasMore()).isFalse();
    }

    @Test
    void ofFactoryMethodWithEmptyListAndNonZeroTotal() {
        List<String> items = List.of();

        PaginatedResponse<String> response = PaginatedResponse.of(items, 0, 0, 20);

        assertThat(response.getResults()).isEmpty();
        assertThat(response.getTotalCount()).isEqualTo(0);
        assertThat(response.getReturnedCount()).isEqualTo(0);
        assertThat(response.getOffset()).isEqualTo(0);
        assertThat(response.getLimit()).isEqualTo(20);
        assertThat(response.isHasMore()).isFalse();
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
                .offset(0)
                .limit(20)
                .hasMore(false)
                .build();

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getReturnedCount()).isEqualTo(1);
        assertThat(response.getOffset()).isEqualTo(0);
        assertThat(response.getLimit()).isEqualTo(20);
        assertThat(response.isHasMore()).isFalse();
        assertThat(response).isInstanceOf(PaginatedResponse.class);
    }

    @Test
    void inheritanceWorksWithCodeSampleSearchResponse() {
        CodeSampleSearchResponse response = CodeSampleSearchResponse.builder()
                .results(List.of())
                .totalCount(0)
                .returnedCount(0)
                .offset(0)
                .limit(10)
                .hasMore(false)
                .build();

        assertThat(response.getResults()).isEmpty();
        assertThat(response.getOffset()).isEqualTo(0);
        assertThat(response.getLimit()).isEqualTo(10);
        assertThat(response.isHasMore()).isFalse();
        assertThat(response).isInstanceOf(PaginatedResponse.class);
    }

    @Test
    void inheritanceWorksWithDocumentSearchResponse() {
        DocumentSearchResponse response = DocumentSearchResponse.builder()
                .results(List.of())
                .totalCount(5)
                .returnedCount(0)
                .offset(0)
                .limit(20)
                .hasMore(true)
                .build();

        assertThat(response.getTotalCount()).isEqualTo(5);
        assertThat(response.getOffset()).isEqualTo(0);
        assertThat(response.getLimit()).isEqualTo(20);
        assertThat(response.isHasMore()).isTrue();
        assertThat(response).isInstanceOf(PaginatedResponse.class);
    }

    @Test
    void twoArgOfDelegatesToFourArgOf() {
        List<String> items = List.of("a", "b", "c");

        PaginatedResponse<String> twoArg = PaginatedResponse.of(items, 10);
        PaginatedResponse<String> fourArg = PaginatedResponse.of(items, 10, 0, items.size());

        assertThat(twoArg.getOffset()).isEqualTo(fourArg.getOffset());
        assertThat(twoArg.getLimit()).isEqualTo(fourArg.getLimit());
        assertThat(twoArg.isHasMore()).isEqualTo(fourArg.isHasMore());
    }
}
