package com.fvd.repository.api;

import com.fvd.repository.domain.GithubFileEntry;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing GitHub file index entries.
 * <p>
 * Stores metadata about files in the documentation repository,
 * including their names, paths, and Git SHA hashes.
 * </p>
 */
public interface GithubIndexRepository {

    /**
     * Checks if a GitHub index exists for the given version.
     *
     * @param version the documentation version to check
     * @return true if an index exists for this version, false otherwise
     */
    boolean exists(String version);

    /**
     * Finds all GitHub file entries for the given version.
     *
     * @param version the documentation version to find
     * @return an Optional containing the list of file entries if the index exists, or empty if not found
     */
    Optional<List<GithubFileEntry>> findByVersion(String version);

    /**
     * Saves (creates or replaces) the GitHub index for a version.
     * <p>
     * If an index already exists for this version, it will be completely replaced.
     * </p>
     *
     * @param version the documentation version
     * @param entries the list of GitHub file entries to store
     */
    void save(String version, List<GithubFileEntry> entries);

    /**
     * Deletes the GitHub index for the given version.
     *
     * @param version the documentation version to delete
     */
    void deleteByVersion(String version);
}
