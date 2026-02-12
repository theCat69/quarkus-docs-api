package com.fvd.repository.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Represents a GitHub file entry from the repository index.
 *
 * @param name the file name
 * @param path the file path in the repository
 * @param sha the Git SHA hash of the file
 */
@RegisterForReflection
public record GithubFileEntry(
        String name,
        String path,
        String sha
) {
}
