package com.fvd.repository.api;

import com.fvd.repository.domain.CodeSampleIndexData;

import java.util.Optional;

/**
 * Repository interface for managing code sample index data.
 * <p>
 * Provides operations for storing, retrieving, and managing code sample indexes
 * that enable searching for code examples by keywords.
 * </p>
 */
public interface CodeSampleIndexRepository {

    /**
     * Checks if a code sample index exists for the given version.
     *
     * @param version the documentation version to check
     * @return true if an index exists for this version, false otherwise
     */
    boolean exists(String version);

    /**
     * Finds the code sample index for the given version.
     *
     * @param version the documentation version to find
     * @return an Optional containing the code sample index if it exists, or empty if not found
     */
    Optional<CodeSampleIndexData> findByVersion(String version);

    /**
     * Saves (creates or replaces) the code sample index for a version.
     * <p>
     * If an index already exists for this version, it will be completely replaced.
     * </p>
     *
     * @param version the documentation version
     * @param data the code sample index data to save
     */
    void save(String version, CodeSampleIndexData data);

    /**
     * Deletes the code sample index for the given version.
     *
     * @param version the documentation version to delete
     */
    void deleteByVersion(String version);
}
