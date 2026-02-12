package com.fvd.repository.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Represents a code sample entry in the code sample index.
 *
 * @param filePath the path of the file containing the code sample
 * @param sectionTitle the title of the section containing this code sample
 * @param language the programming language of the code sample
 * @param content the actual code content
 * @param startLine the starting line number of the code sample
 * @param endLine the ending line number of the code sample
 * @param keywords the list of keyword weights for this code sample
 * @param extension the extension identifier (e.g., "quarkus-core")
 */
@RegisterForReflection
public record CodeSampleEntry(
        String filePath,
        String sectionTitle,
        String language,
        String content,
        int startLine,
        int endLine,
        List<KeywordWeight> keywords,
        String extension
) {
}
