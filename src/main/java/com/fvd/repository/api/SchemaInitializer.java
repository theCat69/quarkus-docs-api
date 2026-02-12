package com.fvd.repository.api;

/**
 * Interface for database schema initialization and management.
 * <p>
 * Implementations handle the creation, initialization, and resetting of
 * the database schema for the documentation index storage.
 * </p>
 */
public interface SchemaInitializer {

    /**
     * Initializes the database schema.
     * <p>
     * Creates all necessary tables and indexes if they don't exist.
     * This method is idempotent and safe to call multiple times.
     * </p>
     */
    void initSchema();

    /**
     * Resets the database schema by dropping and recreating all tables.
     * <p>
     * <strong>Warning:</strong> This operation will delete all data.
     * Use with caution, primarily intended for testing scenarios.
     * </p>
     */
    void resetSchema();
}
