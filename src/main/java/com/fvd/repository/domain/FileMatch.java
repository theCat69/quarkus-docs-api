package com.fvd.repository.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Represents a file match result from a search operation.
 *
 * @param path the file path
 * @param extension the extension identifier
 * @param score the computed relevance score
 * @param matchedKeywords the list of keywords that matched
 */
@RegisterForReflection
public record FileMatch(
        String path,
        String extension,
        double score,
        List<String> matchedKeywords
) {
}
