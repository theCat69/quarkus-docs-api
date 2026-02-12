package com.fvd.repository.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Represents the complete code sample index data for a version.
 *
 * @param version the documentation version (e.g., "main", "3.20")
 * @param samples the list of code sample entries
 */
@RegisterForReflection
public record CodeSampleIndexData(
        String version,
        List<CodeSampleEntry> samples
) {
}
