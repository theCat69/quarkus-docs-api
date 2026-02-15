package com.fvd.indexs.stores;

import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SqliteSchemaInitializer {

    private final DataSource dataSource;
    @ConfigProperty(name = "app.cache.dir", defaultValue = ".cache")
    String cacheDir;

    void onStartup(@Observes @Priority(100) StartupEvent event) {
        initSchema();
    }

    public void initSchema() {
        ensureCacheDir();
        createTables();
    }

    /**
     * Drops all tables and recreates the schema. Used in tests to ensure
     * a clean database after the cache directory has been wiped.
     */
    public void resetSchema() {
        ensureCacheDir();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS code_sample_keywords");
            stmt.execute("DROP TABLE IF EXISTS code_samples");
            stmt.execute("DROP TABLE IF EXISTS section_keywords");
            stmt.execute("DROP TABLE IF EXISTS sections");
            stmt.execute("DROP TABLE IF EXISTS file_keywords");
            stmt.execute("DROP TABLE IF EXISTS document_metadata");
            stmt.execute("DROP TABLE IF EXISTS files");
            stmt.execute("DROP TABLE IF EXISTS github_index");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to drop SQLite tables", e);
        }
        createTables();
    }

    private void ensureCacheDir() {
        if (cacheDir != null) {
            Path cacheDirPath = Path.of(cacheDir);
            try {
                Files.createDirectories(cacheDirPath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create cache directory: " + cacheDir, e);
            }
        }
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA cache_size=-8000");
            stmt.execute("PRAGMA temp_store=MEMORY");

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS files (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        version TEXT NOT NULL,
                        path TEXT NOT NULL,
                        extension TEXT NOT NULL DEFAULT 'quarkus-core',
                        UNIQUE(version, path)
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS file_keywords (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        file_id INTEGER NOT NULL,
                        word TEXT NOT NULL,
                        score INTEGER NOT NULL,
                        source TEXT NOT NULL DEFAULT 'body',
                        frequency INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS sections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        file_id INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        start_line INTEGER NOT NULL,
                        end_line INTEGER NOT NULL,
                        FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS section_keywords (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        section_id INTEGER NOT NULL,
                        word TEXT NOT NULL,
                        score INTEGER NOT NULL,
                        source TEXT NOT NULL DEFAULT 'body',
                        frequency INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY (section_id) REFERENCES sections(id) ON DELETE CASCADE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS github_index (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        version TEXT NOT NULL,
                        name TEXT NOT NULL,
                        path TEXT NOT NULL,
                        sha TEXT NOT NULL
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS code_samples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        version TEXT NOT NULL,
                        file_path TEXT NOT NULL,
                        section_title TEXT NOT NULL,
                        language TEXT NOT NULL,
                        content TEXT NOT NULL,
                        start_line INTEGER NOT NULL,
                        end_line INTEGER NOT NULL,
                        extension TEXT NOT NULL DEFAULT 'quarkus-core'
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS code_sample_keywords (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        sample_id INTEGER NOT NULL,
                        word TEXT NOT NULL,
                        score INTEGER NOT NULL,
                        FOREIGN KEY (sample_id) REFERENCES code_samples(id) ON DELETE CASCADE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS document_metadata (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        file_id INTEGER NOT NULL UNIQUE,
                        categories TEXT,
                        topics TEXT,
                        extensions_gav TEXT,
                        summary TEXT,
                        diataxis_type TEXT,
                        FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
                    )
                    """);

            // Create indexes for efficient lookups
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_files_version ON files(version)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_keywords_file_id ON file_keywords(file_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_keywords_word ON file_keywords(word)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sections_file_id ON sections(file_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_section_keywords_section_id ON section_keywords(section_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_section_keywords_word ON section_keywords(word)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_github_index_version ON github_index(version)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_code_samples_version ON code_samples(version)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_code_sample_keywords_sample_id ON code_sample_keywords(sample_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_code_sample_keywords_word ON code_sample_keywords(word)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_document_metadata_file_id ON document_metadata(file_id)");

            log.info("SQLite schema initialized successfully");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize SQLite schema", e);
        }
    }
}
