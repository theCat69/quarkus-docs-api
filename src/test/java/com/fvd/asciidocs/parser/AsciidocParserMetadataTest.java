package com.fvd.asciidocs.parser;

import com.fvd.asciidocs.model.DocumentMetadata;
import com.fvd.search.TestSearchConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AsciidocParserMetadataTest {

    private final AsciidocParser parser = new AsciidocParser(new TestSearchConfig());

    @Test
    void extractMetadataWithAllAttributes() {
        String content = """
                = OpenID Connect (OIDC) Authorization Code Flow
                include::_attributes.adoc[]
                :categories: security,web
                :topics: security,oidc,authentication,authorization
                :extensions: io.quarkus:quarkus-oidc
                :summary: OIDC Authorization Code Flow mechanism for protecting web applications
                :diataxis-type: reference
                
                == Introduction
                
                Some content here.
                """;

        DocumentMetadata metadata = parser.extractMetadata(content);

        assertThat(metadata.getCategories()).containsExactly("security", "web");
        assertThat(metadata.getTopics()).containsExactly("security", "oidc", "authentication", "authorization");
        assertThat(metadata.getExtensions()).containsExactly("io.quarkus:quarkus-oidc");
        assertThat(metadata.getSummary()).isEqualTo("OIDC Authorization Code Flow mechanism for protecting web applications");
        assertThat(metadata.getDiataxisType()).isEqualTo("reference");
    }

    @Test
    void extractMetadataWithOnlyCategories() {
        String content = """
                = Simple Guide
                :categories: data
                
                == Content
                
                Some text.
                """;

        DocumentMetadata metadata = parser.extractMetadata(content);

        assertThat(metadata.getCategories()).containsExactly("data");
        assertThat(metadata.getTopics()).isEmpty();
        assertThat(metadata.getExtensions()).isEmpty();
        assertThat(metadata.getSummary()).isNull();
        assertThat(metadata.getDiataxisType()).isNull();
    }

    @Test
    void extractMetadataWithNoMetadataAttributes() {
        String content = """
                = A Guide Without Metadata
                
                == Introduction
                
                Just some text without any metadata attributes.
                """;

        DocumentMetadata metadata = parser.extractMetadata(content);

        assertThat(metadata.getCategories()).isEmpty();
        assertThat(metadata.getTopics()).isEmpty();
        assertThat(metadata.getExtensions()).isEmpty();
        assertThat(metadata.getSummary()).isNull();
        assertThat(metadata.getDiataxisType()).isNull();
    }

    @Test
    void extractMetadataWithMultiValueCategories() {
        String content = """
                = Guide
                :categories: security,web,data
                
                == Section
                """;

        DocumentMetadata metadata = parser.extractMetadata(content);

        assertThat(metadata.getCategories()).containsExactly("security", "web", "data");
    }

    @Test
    void extractMetadataWithExtensionContainingColons() {
        String content = """
                = Guide
                :extensions: io.quarkus:quarkus-rest
                
                == Section
                """;

        DocumentMetadata metadata = parser.extractMetadata(content);

        assertThat(metadata.getExtensions()).containsExactly("io.quarkus:quarkus-rest");
    }

    @Test
    void extractMetadataWithMultipleExtensions() {
        String content = """
                = Guide
                :extensions: io.quarkus:quarkus-rest,io.quarkus:quarkus-rest-jackson
                
                == Section
                """;

        DocumentMetadata metadata = parser.extractMetadata(content);

        assertThat(metadata.getExtensions()).containsExactly(
                "io.quarkus:quarkus-rest", "io.quarkus:quarkus-rest-jackson");
    }

    @Test
    void extractMetadataIgnoresAttributesInsideCodeBlock() {
        String content = """
                = Guide With Code Block
                :categories: web
                
                == Section
                
                Some content.
                
                [source,asciidoc]
                ----
                :categories: fake,should-not-be-parsed
                :topics: fake-topic
                ----
                
                More content.
                """;

        DocumentMetadata metadata = parser.extractMetadata(content);

        // Only header attributes should be parsed, not code block content
        assertThat(metadata.getCategories()).containsExactly("web");
        assertThat(metadata.getTopics()).isEmpty();
    }

    @Test
    void extractMetadataReturnsEmptyForNullContent() {
        DocumentMetadata metadata = parser.extractMetadata(null);

        assertThat(metadata.getCategories()).isEmpty();
        assertThat(metadata.getTopics()).isEmpty();
        assertThat(metadata.getExtensions()).isEmpty();
    }

    @Test
    void extractMetadataReturnsEmptyForBlankContent() {
        DocumentMetadata metadata = parser.extractMetadata("   ");

        assertThat(metadata.getCategories()).isEmpty();
        assertThat(metadata.getTopics()).isEmpty();
        assertThat(metadata.getExtensions()).isEmpty();
    }

    @Test
    void extractMetadataHandlesHeaderWithBlankLinesBetweenAttributes() {
        String content = """
                = Title
                include::_attributes.adoc[]
                
                :categories: security
                
                :topics: oidc,auth
                
                == First Section
                """;

        DocumentMetadata metadata = parser.extractMetadata(content);

        // All attributes before the first == heading should be parsed
        assertThat(metadata.getCategories()).containsExactly("security");
        assertThat(metadata.getTopics()).containsExactly("oidc", "auth");
    }

    @Test
    void extractMetadataDoesNotParseAttributesAfterFirstSectionHeader() {
        String content = """
                = Title
                :categories: web
                
                == Section One
                
                :topics: fake-topic-in-body
                
                Regular content.
                """;

        DocumentMetadata metadata = parser.extractMetadata(content);

        assertThat(metadata.getCategories()).containsExactly("web");
        assertThat(metadata.getTopics()).isEmpty();
    }

    @Test
    void extractHeaderBlockStopsAtFirstLevelTwoHeading() {
        String content = """
                = Title
                :categories: core
                :summary: A summary
                
                == Introduction
                
                Body content with :topics: fake
                """;

        String header = parser.extractHeaderBlock(content);

        assertThat(header).contains(":categories: core");
        assertThat(header).contains(":summary: A summary");
        assertThat(header).doesNotContain("== Introduction");
        assertThat(header).doesNotContain(":topics: fake");
    }

    @Test
    void extractMetadataWithDiataxisType() {
        String content = """
                = Tutorial Guide
                :diataxis-type: tutorial
                
                == Getting Started
                """;

        DocumentMetadata metadata = parser.extractMetadata(content);

        assertThat(metadata.getDiataxisType()).isEqualTo("tutorial");
    }

    @Test
    void extractMetadataWithComplexRealWorldHeader() {
        String content = """
                = Security OpenID Connect (OIDC) multi-tenancy
                include::_attributes.adoc[]
                :categories: security,web
                :topics: security,oidc,authentication,authorization,multi-tenancy
                :extensions: io.quarkus:quarkus-oidc
                :summary: How to use OpenID Connect (OIDC) multi-tenancy to support multiple tenants
                :diataxis-type: howto
                
                :toc: left
                :toclevels: 3
                
                == Introduction
                
                This guide explains multi-tenancy support.
                """;

        DocumentMetadata metadata = parser.extractMetadata(content);

        assertThat(metadata.getCategories()).containsExactly("security", "web");
        assertThat(metadata.getTopics()).containsExactly("security", "oidc", "authentication", "authorization", "multi-tenancy");
        assertThat(metadata.getExtensions()).containsExactly("io.quarkus:quarkus-oidc");
        assertThat(metadata.getSummary()).isEqualTo("How to use OpenID Connect (OIDC) multi-tenancy to support multiple tenants");
        assertThat(metadata.getDiataxisType()).isEqualTo("howto");
    }
}
