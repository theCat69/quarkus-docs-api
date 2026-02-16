package com.fvd.api.dto;

import com.fvd.common.exceptions.InvalidInputException;
import com.fvd.common.validators.InputValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchParamsTest {

    @Test
    void fromRawHappyPath() {
        SearchParams params = SearchParams.fromRaw(
                "3.27", "security oidc", "quarkus-core", 10, 5);

        assertThat(params.version()).isEqualTo("3.27");
        assertThat(params.q()).isEqualTo("security oidc");
        assertThat(params.extension()).isEqualTo("quarkus-core");
        assertThat(params.limit()).isEqualTo(10);
        assertThat(params.offset()).isEqualTo(5);
    }

    @Test
    void fromRawWithNullVersionDefaultsToMain() {
        SearchParams params = SearchParams.fromRaw(
                null, "security", null, null, null);

        assertThat(params.version()).isEqualTo("main");
    }

    @Test
    void fromRawWithNullLimitDefaultsTo20() {
        SearchParams params = SearchParams.fromRaw(
                "main", "security", null, null, null);

        assertThat(params.limit()).isEqualTo(20);
    }

    @Test
    void fromRawWithNullOffsetDefaultsTo0() {
        SearchParams params = SearchParams.fromRaw(
                "main", "security", null, null, null);

        assertThat(params.offset()).isEqualTo(0);
    }

    @Test
    void fromRawWithBlankExtensionNormalizesToNull() {
        SearchParams params = SearchParams.fromRaw(
                "main", "security", "   ", null, null);

        assertThat(params.extension()).isNull();
    }

    @Test
    void fromRawTrimsExtension() {
        SearchParams params = SearchParams.fromRaw(
                "main", "security", "  quarkus-core  ", null, null);

        assertThat(params.extension()).isEqualTo("quarkus-core");
    }

    @Test
    void fromRawWithEmptyQThrowsInvalidInputException() {
        assertThatThrownBy(() -> SearchParams.fromRaw(
                "main", "", null, null, null))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void fromRawWithNullQThrowsInvalidInputException() {
        assertThatThrownBy(() -> SearchParams.fromRaw(
                "main", null, null, null, null))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void fromRawPreservesQueryAsIs() {
        SearchParams params = SearchParams.fromRaw(
                "main", "how does the reactive work", null, null, null);

        assertThat(params.q()).isEqualTo("how does the reactive work");
    }

    @Test
    void fromRawWithQueryExceedingMaxLengthThrows() {
        String longQuery = "a".repeat(InputValidator.MAX_QUERY_LENGTH + 1);
        assertThatThrownBy(() -> SearchParams.fromRaw(
                "main", longQuery, null, null, null))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("q must not exceed " + InputValidator.MAX_QUERY_LENGTH + " characters");
    }

    @Test
    void fromRawWithQueryAtMaxLengthSucceeds() {
        String exactQuery = "a".repeat(InputValidator.MAX_QUERY_LENGTH);
        SearchParams params = SearchParams.fromRaw(
                "main", exactQuery, null, null, null);

        assertThat(params.q()).isEqualTo(exactQuery);
    }

    @Test
    void fromRawWithExtensionExceedingMaxLengthThrows() {
        String longExtension = "x".repeat(InputValidator.MAX_FILTER_LENGTH + 1);
        assertThatThrownBy(() -> SearchParams.fromRaw(
                "main", "security", longExtension, null, null))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("extension must not exceed " + InputValidator.MAX_FILTER_LENGTH + " characters");
    }

    @Test
    void fromRawWithExtensionAtMaxLengthSucceeds() {
        String exactExtension = "x".repeat(InputValidator.MAX_FILTER_LENGTH);
        SearchParams params = SearchParams.fromRaw(
                "main", "security", exactExtension, null, null);

        assertThat(params.extension()).isEqualTo(exactExtension);
    }
}
