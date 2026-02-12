package com.fvd.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchConstantsTest {

    @Test
    void defaultLimitIs20() {
        assertThat(SearchConstants.DEFAULT_LIMIT).isEqualTo(20);
    }

    @Test
    void maxLimitIs100() {
        assertThat(SearchConstants.MAX_LIMIT).isEqualTo(100);
    }

    @Test
    void defaultOffsetIs0() {
        assertThat(SearchConstants.DEFAULT_OFFSET).isEqualTo(0);
    }

    @Test
    void snippetContextSizeIs80() {
        assertThat(SearchConstants.SNIPPET_CONTEXT_SIZE).isEqualTo(80);
    }
}
