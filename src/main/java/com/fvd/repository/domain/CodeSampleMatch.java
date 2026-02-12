package com.fvd.repository.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Represents a code sample match result from a search operation.
 *
 * @param path the file path containing the code sample
 * @param sectionTitle the title of the section containing the code sample
 * @param language the programming language of the code sample
 * @param content the code content
 * @param startLine the starting line number of the code sample
 * @param endLine the ending line number of the code sample
 * @param extension the extension identifier
 * @param score the computed relevance score
 * @param matchedKeywords the list of keywords that matched
 */
@RegisterForReflection
public record CodeSampleMatch(
        String path,
        String sectionTitle,
        String language,
        String content,
        int startLine,
        int endLine,
        String extension,
        double score,
        List<String> matchedKeywords
) {
}
