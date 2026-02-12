package com.fvd.repository.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Represents a section within a file with its keyword weights.
 *
 * @param title the section title
 * @param startLine the starting line number of the section
 * @param endLine the ending line number of the section
 * @param keywords the list of keyword weights for this section
 */
@RegisterForReflection
public record SectionEntry(
        String title,
        int startLine,
        int endLine,
        List<KeywordWeight> keywords
) {
}
