package com.fvd.asciidocs.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Document metadata extracted from AsciiDoc header attributes.
 * Represents structured data from :categories:, :topics:, :extensions:,
 * :summary:, and :diataxis-type: attributes.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RegisterForReflection
public class DocumentMetadata {

    /** Comma-separated category tags (e.g., "security", "web", "data") */
    private List<String> categories;

    /** Comma-separated topic tags (e.g., "rest", "resteasy-reactive") */
    private List<String> topics;

    /** Extension GAV coordinates (e.g., "io.quarkus:quarkus-rest") */
    private List<String> extensions;

    /** Human-readable one-line description */
    private String summary;

    /** Diataxis documentation type: reference, concept, tutorial, howto */
    private String diataxisType;

    /**
     * Returns an empty metadata instance with empty lists and null strings.
     */
    public static DocumentMetadata empty() {
        return DocumentMetadata.builder()
                .categories(List.of())
                .topics(List.of())
                .extensions(List.of())
                .build();
    }

    /**
     * Creates a DocumentMetadata from a map of AsciiDoc header attributes.
     * Parses :categories:, :topics:, :extensions: as comma-separated lists,
     * and :summary:, :diataxis-type: as plain strings.
     *
     * @param attributes map of attribute name to value
     * @return populated DocumentMetadata
     */
    public static DocumentMetadata fromAttributes(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return empty();
        }
        return DocumentMetadata.builder()
                .categories(parseCommaSeparated(attributes.get("categories")))
                .topics(parseCommaSeparated(attributes.get("topics")))
                .extensions(parseCommaSeparated(attributes.get("extensions")))
                .summary(attributes.get("summary"))
                .diataxisType(attributes.get("diataxis-type"))
                .build();
    }

    public boolean hasCategories() {
        return categories != null && !categories.isEmpty();
    }

    public boolean hasTopics() {
        return topics != null && !topics.isEmpty();
    }

    public boolean hasSummary() {
        return summary != null && !summary.isBlank();
    }

    private static List<String> parseCommaSeparated(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
