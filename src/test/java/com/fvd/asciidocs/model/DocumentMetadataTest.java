package com.fvd.asciidocs.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentMetadataTest {

    @Test
    void emptyReturnsEmptyListsAndNullStrings() {
        DocumentMetadata metadata = DocumentMetadata.empty();

        assertThat(metadata.getCategories()).isEmpty();
        assertThat(metadata.getTopics()).isEmpty();
        assertThat(metadata.getExtensions()).isEmpty();
        assertThat(metadata.getSummary()).isNull();
        assertThat(metadata.getDiataxisType()).isNull();
    }

    @Test
    void fromAttributesParsesAllFields() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("categories", "security,web");
        attributes.put("topics", "security,oidc,authentication");
        attributes.put("extensions", "io.quarkus:quarkus-oidc");
        attributes.put("summary", "OIDC Authorization Code Flow");
        attributes.put("diataxis-type", "reference");

        DocumentMetadata metadata = DocumentMetadata.fromAttributes(attributes);

        assertThat(metadata.getCategories()).containsExactly("security", "web");
        assertThat(metadata.getTopics()).containsExactly("security", "oidc", "authentication");
        assertThat(metadata.getExtensions()).containsExactly("io.quarkus:quarkus-oidc");
        assertThat(metadata.getSummary()).isEqualTo("OIDC Authorization Code Flow");
        assertThat(metadata.getDiataxisType()).isEqualTo("reference");
    }

    @Test
    void fromAttributesParsesMultipleExtensionsWithColons() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("extensions", "io.quarkus:quarkus-rest,io.quarkus:quarkus-rest-jackson");

        DocumentMetadata metadata = DocumentMetadata.fromAttributes(attributes);

        assertThat(metadata.getExtensions()).containsExactly(
                "io.quarkus:quarkus-rest", "io.quarkus:quarkus-rest-jackson");
    }

    @Test
    void fromAttributesHandlesSpacesInCommaSeparatedValues() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("categories", " security , web , data ");
        attributes.put("topics", "rest, resteasy-reactive, virtual-threads");

        DocumentMetadata metadata = DocumentMetadata.fromAttributes(attributes);

        assertThat(metadata.getCategories()).containsExactly("security", "web", "data");
        assertThat(metadata.getTopics()).containsExactly("rest", "resteasy-reactive", "virtual-threads");
    }

    @Test
    void fromAttributesWithMissingAttributesReturnsEmptyListsAndNulls() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("categories", "security");

        DocumentMetadata metadata = DocumentMetadata.fromAttributes(attributes);

        assertThat(metadata.getCategories()).containsExactly("security");
        assertThat(metadata.getTopics()).isEmpty();
        assertThat(metadata.getExtensions()).isEmpty();
        assertThat(metadata.getSummary()).isNull();
        assertThat(metadata.getDiataxisType()).isNull();
    }

    @Test
    void fromAttributesWithNullMapReturnsEmpty() {
        DocumentMetadata metadata = DocumentMetadata.fromAttributes(null);

        assertThat(metadata.getCategories()).isEmpty();
        assertThat(metadata.getTopics()).isEmpty();
        assertThat(metadata.getExtensions()).isEmpty();
        assertThat(metadata.getSummary()).isNull();
        assertThat(metadata.getDiataxisType()).isNull();
    }

    @Test
    void fromAttributesWithEmptyMapReturnsEmpty() {
        DocumentMetadata metadata = DocumentMetadata.fromAttributes(Map.of());

        assertThat(metadata.getCategories()).isEmpty();
        assertThat(metadata.getTopics()).isEmpty();
        assertThat(metadata.getExtensions()).isEmpty();
    }

    @Test
    void fromAttributesWithBlankValuesReturnsEmptyLists() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("categories", "   ");
        attributes.put("topics", "");

        DocumentMetadata metadata = DocumentMetadata.fromAttributes(attributes);

        assertThat(metadata.getCategories()).isEmpty();
        assertThat(metadata.getTopics()).isEmpty();
    }

    @Test
    void hasCategoriesReturnsTrueWhenPresent() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .categories(java.util.List.of("security"))
                .topics(java.util.List.of())
                .extensions(java.util.List.of())
                .build();

        assertThat(metadata.hasCategories()).isTrue();
        assertThat(metadata.hasTopics()).isFalse();
    }

    @Test
    void hasCategoriesReturnsFalseForNull() {
        DocumentMetadata metadata = DocumentMetadata.builder().build();

        assertThat(metadata.hasCategories()).isFalse();
    }

    @Test
    void hasSummaryReturnsTrueWhenPresent() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .summary("A description")
                .categories(java.util.List.of())
                .topics(java.util.List.of())
                .extensions(java.util.List.of())
                .build();

        assertThat(metadata.hasSummary()).isTrue();
    }

    @Test
    void hasSummaryReturnsFalseForNull() {
        DocumentMetadata metadata = DocumentMetadata.empty();

        assertThat(metadata.hasSummary()).isFalse();
    }

    @Test
    void hasSummaryReturnsFalseForBlank() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .summary("   ")
                .categories(java.util.List.of())
                .topics(java.util.List.of())
                .extensions(java.util.List.of())
                .build();

        assertThat(metadata.hasSummary()).isFalse();
    }
}
