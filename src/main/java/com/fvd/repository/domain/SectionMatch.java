package com.fvd.repository.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Represents a section match result from a search operation.
 *
 * @param path the file path containing the section
 * @param sectionTitle the title of the matched section
 * @param startLine the starting line number of the section
 * @param endLine the ending line number of the section
 * @param extension the extension identifier
 * @param score the computed relevance score
 * @param matchedKeywords the list of keywords that matched
 */
@RegisterForReflection
public record SectionMatch(
        String path,
        String sectionTitle,
        int startLine,
        int endLine,
        String extension,
        double score,
        List<String> matchedKeywords
) {
}
