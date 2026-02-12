package com.fvd.repository.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Represents the complete keyword index data for a version.
 * Contains all file entries with their keywords and sections.
 *
 * @param version the documentation version (e.g., "main", "3.20")
 * @param files the list of file entries in this index
 */
@RegisterForReflection
public record KeywordIndexData(
        String version,
        List<FileEntry> files
) {
}
