package com.fvd.repository.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Represents a file entry in the keyword index with its keywords and sections.
 *
 * @param path the file path relative to the documentation root
 * @param extension the extension identifier (e.g., "quarkus-core" or quarkiverse extension name)
 * @param keywords the list of keyword weights at the file level
 * @param sections the list of sections within this file
 */
@RegisterForReflection
public record FileEntry(
        String path,
        String extension,
        List<KeywordWeight> keywords,
        List<SectionEntry> sections
) {
}
