package com.fvd.common.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilterUtilsTest {

    @Test
    void matchesFilterWithNullFilterReturnsTrue() {
        assertThat(FilterUtils.matchesFilter(null, "any-value")).isTrue();
    }

    @Test
    void matchesFilterWithBlankFilterReturnsTrue() {
        assertThat(FilterUtils.matchesFilter("   ", "any-value")).isTrue();
    }

    @Test
    void matchesFilterWithEmptyFilterReturnsTrue() {
        assertThat(FilterUtils.matchesFilter("", "any-value")).isTrue();
    }

    @Test
    void matchesFilterWithMatchingValueReturnsTrue() {
        assertThat(FilterUtils.matchesFilter("security", "security")).isTrue();
    }

    @Test
    void matchesFilterWithNonMatchingValueReturnsFalse() {
        assertThat(FilterUtils.matchesFilter("security", "rest-apis")).isFalse();
    }

    @Test
    void matchesFilterWithNullValueAndNullFilterReturnsTrue() {
        assertThat(FilterUtils.matchesFilter(null, null)).isTrue();
    }

    @Test
    void matchesFilterWithNonNullFilterAndNullValueReturnsFalse() {
        assertThat(FilterUtils.matchesFilter("security", null)).isFalse();
    }

    @Test
    void matchesFilterIsCaseSensitive() {
        assertThat(FilterUtils.matchesFilter("Security", "security")).isFalse();
    }
}
