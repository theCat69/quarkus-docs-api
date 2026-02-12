package com.fvd.repository.api;

import com.fvd.repository.domain.KeywordIndexData;

import java.util.Optional;

/**
 * Repository interface for managing keyword index data.
 * <p>
 * Provides operations for storing, retrieving, and managing keyword indexes
 * that map documentation files and sections to their associated keywords.
 * </p>
 */
public interface KeywordIndexRepository {

    /**
     * Checks if a keyword index exists for the given version.
     *
     * @param version the documentation version to check
     * @return true if an index exists for this version, false otherwise
     */
    boolean exists(String version);

    /**
     * Finds the keyword index for the given version.
     *
     * @param version the documentation version to find
     * @return an Optional containing the keyword index if it exists, or empty if not found
     */
    Optional<KeywordIndexData> findByVersion(String version);

    /**
     * Saves (creates or replaces) the keyword index for a version.
     * <p>
     * If an index already exists for this version, it will be completely replaced.
     * </p>
     *
     * @param version the documentation version
     * @param data the keyword index data to save
     */
    void save(String version, KeywordIndexData data);

    /**
     * Deletes the keyword index for the given version.
     *
     * @param version the documentation version to delete
     */
    void deleteByVersion(String version);
}
