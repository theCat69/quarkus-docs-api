package com.fvd.common.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlBuilderTest {

    private final UrlBuilder urlBuilder = new UrlBuilder();

    @Test
    void buildUrlWithPageOnly() {
        assertThat(urlBuilder.buildUrl("rest-client"))
                .isEqualTo("https://quarkus.io/guides/rest-client");
    }

    @Test
    void buildUrlWithPageAndSectionTitle() {
        assertThat(urlBuilder.buildUrl("rest-client", "Getting Started"))
                .isEqualTo("https://quarkus.io/guides/rest-client#getting-started");
    }

    @Test
    void buildUrlStripsAdocExtension() {
        assertThat(urlBuilder.buildUrl("rest-client.adoc"))
                .isEqualTo("https://quarkus.io/guides/rest-client");
    }

    @Test
    void buildUrlStripsAdocExtensionWithSection() {
        assertThat(urlBuilder.buildUrl("rest-client.adoc", "Getting Started"))
                .isEqualTo("https://quarkus.io/guides/rest-client#getting-started");
    }

    @Test
    void buildUrlHandlesSpecialCharactersInSectionTitle() {
        assertThat(urlBuilder.buildUrl("rest-client", "What's New?"))
                .isEqualTo("https://quarkus.io/guides/rest-client#whats-new");
    }

    @Test
    void buildUrlWithNullSectionTitleProducesPageUrl() {
        assertThat(urlBuilder.buildUrl("rest-client", null))
                .isEqualTo("https://quarkus.io/guides/rest-client");
    }

    @Test
    void buildUrlWithEmptySectionTitleProducesPageUrl() {
        assertThat(urlBuilder.buildUrl("rest-client", ""))
                .isEqualTo("https://quarkus.io/guides/rest-client");
    }

    @Test
    void buildUrlWithBlankSectionTitleProducesPageUrl() {
        assertThat(urlBuilder.buildUrl("rest-client", "   "))
                .isEqualTo("https://quarkus.io/guides/rest-client");
    }

    @Test
    void buildUrlCollapsesConsecutiveDashesInSlug() {
        assertThat(urlBuilder.buildUrl("security", "Using -- Advanced Config"))
                .isEqualTo("https://quarkus.io/guides/security#using-advanced-config");
    }

    @Test
    void buildUrlStripsLeadingAndTrailingDashesFromSlug() {
        assertThat(urlBuilder.buildUrl("rest-client", "!Hello World!"))
                .isEqualTo("https://quarkus.io/guides/rest-client#hello-world");
    }

    @Test
    void toSlugShouldConvertToUrlSafeSlug() {
        assertThat(urlBuilder.toSlug("Getting Started")).isEqualTo("getting-started");
        assertThat(urlBuilder.toSlug("What's New?")).isEqualTo("whats-new");
        assertThat(urlBuilder.toSlug("")).isEqualTo("");
        assertThat(urlBuilder.toSlug(null)).isEqualTo("");
    }
}
