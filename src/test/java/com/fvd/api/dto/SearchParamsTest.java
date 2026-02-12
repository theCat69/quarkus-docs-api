package com.fvd.api.dto;

import com.fvd.common.exceptions.InvalidInputException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchParamsTest {

    @Test
    void fromRawHappyPath() {
        SearchParams params = SearchParams.fromRaw(
                "3.27", "security oidc", "security", "quarkus-core", 10, 5);

        assertThat(params.version()).isEqualTo("3.27");
        assertThat(params.keywords()).containsExactly("security", "oidc");
        assertThat(params.subject()).isEqualTo("security");
        assertThat(params.extension()).isEqualTo("quarkus-core");
        assertThat(params.limit()).isEqualTo(10);
        assertThat(params.offset()).isEqualTo(5);
    }

    @Test
    void fromRawWithNullVersionDefaultsToMain() {
        SearchParams params = SearchParams.fromRaw(
                null, "security", null, null, null, null);

        assertThat(params.version()).isEqualTo("main");
    }

    @Test
    void fromRawWithNullLimitDefaultsTo20() {
        SearchParams params = SearchParams.fromRaw(
                "main", "security", null, null, null, null);

        assertThat(params.limit()).isEqualTo(20);
    }

    @Test
    void fromRawWithNullOffsetDefaultsTo0() {
        SearchParams params = SearchParams.fromRaw(
                "main", "security", null, null, null, null);

        assertThat(params.offset()).isEqualTo(0);
    }

    @Test
    void fromRawWithBlankSubjectNormalizesToNull() {
        SearchParams params = SearchParams.fromRaw(
                "main", "security", "   ", null, null, null);

        assertThat(params.subject()).isNull();
    }

    @Test
    void fromRawWithBlankExtensionNormalizesToNull() {
        SearchParams params = SearchParams.fromRaw(
                "main", "security", null, "   ", null, null);

        assertThat(params.extension()).isNull();
    }

    @Test
    void fromRawTrimsSubjectAndExtension() {
        SearchParams params = SearchParams.fromRaw(
                "main", "security", "  security  ", "  quarkus-core  ", null, null);

        assertThat(params.subject()).isEqualTo("security");
        assertThat(params.extension()).isEqualTo("quarkus-core");
    }

    @Test
    void fromRawWithEmptyKeywordsThrowsInvalidInputException() {
        assertThatThrownBy(() -> SearchParams.fromRaw(
                "main", "", null, null, null, null))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void fromRawWithNullKeywordsThrowsInvalidInputException() {
        assertThatThrownBy(() -> SearchParams.fromRaw(
                "main", null, null, null, null, null))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void fromRawWithAllStopWordsKeywordsThrowsInvalidInputException() {
        assertThatThrownBy(() -> SearchParams.fromRaw(
                "main", "how does the", null, null, null, null))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("All keywords are stop words");
    }
}
