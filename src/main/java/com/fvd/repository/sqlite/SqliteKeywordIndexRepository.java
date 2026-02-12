package com.fvd.repository.sqlite;

import com.fvd.common.validators.InputValidator;
import com.fvd.repository.api.KeywordIndexRepository;
import com.fvd.repository.domain.FileEntry;
import com.fvd.repository.domain.KeywordIndexData;
import com.fvd.repository.domain.KeywordWeight;
import com.fvd.repository.domain.SectionEntry;
import com.fvd.repository.exceptions.RepositoryException;
import com.fvd.search.services.KeywordScorer;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * SQLite implementation of {@link KeywordIndexRepository}.
 * <p>
 * Stores keyword index data in SQLite tables with support for
 * file-level and section-level keyword associations.
 * </p>
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@LookupIfProperty(name = "app.database.type", stringValue = "sqlite", lookupIfMissing = true)
public class SqliteKeywordIndexRepository implements KeywordIndexRepository {

    private final DataSource dataSource;

    @Override
    public boolean exists(String version) {
        InputValidator.validateVersion(version);

        try (Connection conn = dataSource.getConnection()) {
            return SqlUtils.existsByVersion(conn, "files", "version", version);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to check keyword index existence for version: " + version, e);
        }
    }

    @Override
    public Optional<KeywordIndexData> findByVersion(String version) {
        InputValidator.validateVersion(version);

        try (Connection conn = dataSource.getConnection()) {
            List<FileEntry> fileEntries = loadFileEntries(conn, version);
            if (fileEntries.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new KeywordIndexData(version, fileEntries));
        } catch (SQLException e) {
            throw new RepositoryException("Failed to read keyword index for version: " + version, e);
        }
    }

    @Override
    public void save(String version, KeywordIndexData data) {
        InputValidator.validateVersion(version);
        Objects.requireNonNull(data, "data must not be null");

        TransactionTemplate.executeInTransactionVoid(dataSource, conn -> {
            deleteVersion(conn, version);
            insertIndex(conn, version, data);
        }, "Failed to write keyword index for version: " + version);
    }

    @Override
    public void deleteByVersion(String version) {
        InputValidator.validateVersion(version);

        TransactionTemplate.executeInTransactionVoid(dataSource, conn -> {
            deleteVersion(conn, version);
        }, "Failed to delete keyword index for version: " + version);
    }

    private void deleteVersion(Connection conn, String version) throws SQLException {
        // Due to ON DELETE CASCADE, deleting files also removes file_keywords, sections, section_keywords
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM files WHERE version = ?")) {
            stmt.setString(1, version);
            stmt.executeUpdate();
        }
    }

    private void insertIndex(Connection conn, String version, KeywordIndexData data) throws SQLException {
        if (data.files() == null || data.files().isEmpty()) {
            return;
        }

        try (PreparedStatement fileStmt = conn.prepareStatement(
                     "INSERT INTO files (version, path, extension) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS);
             PreparedStatement fileKwStmt = conn.prepareStatement(
                     "INSERT INTO file_keywords (file_id, word, score, source, frequency) VALUES (?, ?, ?, ?, ?)");
             PreparedStatement sectionStmt = conn.prepareStatement(
                     "INSERT INTO sections (file_id, title, start_line, end_line) VALUES (?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS);
             PreparedStatement sectionKwStmt = conn.prepareStatement(
                     "INSERT INTO section_keywords (section_id, word, score, source, frequency) VALUES (?, ?, ?, ?, ?)")) {

            for (FileEntry file : data.files()) {
                fileStmt.setString(1, version);
                fileStmt.setString(2, file.path());
                fileStmt.setString(3, file.extension() != null ? file.extension() : "quarkus-core");
                fileStmt.executeUpdate();

                long fileId;
                try (ResultSet keys = fileStmt.getGeneratedKeys()) {
                    keys.next();
                    fileId = keys.getLong(1);
                }

                // Insert file-level keywords with source and frequency
                if (file.keywords() != null) {
                    for (KeywordWeight kw : file.keywords()) {
                        fileKwStmt.setLong(1, fileId);
                        fileKwStmt.setString(2, kw.word());
                        fileKwStmt.setInt(3, (int) kw.weight());
                        fileKwStmt.setString(4, kw.source() != null ? kw.source() : KeywordScorer.SOURCE_BODY);
                        fileKwStmt.setInt(5, kw.frequency() > 0 ? kw.frequency() : 1);
                        fileKwStmt.addBatch();
                    }
                    fileKwStmt.executeBatch();
                }

                // Insert sections and section keywords
                if (file.sections() != null) {
                    for (SectionEntry section : file.sections()) {
                        sectionStmt.setLong(1, fileId);
                        sectionStmt.setString(2, section.title());
                        sectionStmt.setInt(3, section.startLine());
                        sectionStmt.setInt(4, section.endLine());
                        sectionStmt.executeUpdate();

                        long sectionId;
                        try (ResultSet keys = sectionStmt.getGeneratedKeys()) {
                            keys.next();
                            sectionId = keys.getLong(1);
                        }

                        if (section.keywords() != null) {
                            for (KeywordWeight kw : section.keywords()) {
                                sectionKwStmt.setLong(1, sectionId);
                                sectionKwStmt.setString(2, kw.word());
                                sectionKwStmt.setInt(3, (int) kw.weight());
                                sectionKwStmt.setString(4, kw.source() != null ? kw.source() : KeywordScorer.SOURCE_BODY);
                                sectionKwStmt.setInt(5, kw.frequency() > 0 ? kw.frequency() : 1);
                                sectionKwStmt.addBatch();
                            }
                            sectionKwStmt.executeBatch();
                        }
                    }
                }
            }
        }
    }

    private List<FileEntry> loadFileEntries(Connection conn, String version) throws SQLException {
        // Use LinkedHashMap to preserve insertion order (by file id)
        Map<Long, FileEntryBuilder> filesById = new LinkedHashMap<>();

        // Query 1: files + file keywords via JOIN
        try (PreparedStatement stmt = conn.prepareStatement("""
                SELECT f.id AS file_id, f.path, f.extension, fk.word, fk.score, fk.source, fk.frequency
                FROM files f
                LEFT JOIN file_keywords fk ON fk.file_id = f.id
                WHERE f.version = ?
                ORDER BY f.id, fk.score DESC
                """)) {
            stmt.setString(1, version);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long fileId = rs.getLong("file_id");
                    FileEntryBuilder builder = filesById.get(fileId);
                    if (builder == null) {
                        String path = rs.getString("path");
                        String extension = rs.getString("extension");
                        builder = new FileEntryBuilder(path, extension);
                        filesById.put(fileId, builder);
                    }
                    String word = rs.getString("word");
                    if (word != null) {
                        int score = rs.getInt("score");
                        String source = rs.getString("source");
                        int frequency = rs.getInt("frequency");
                        builder.keywords.add(new KeywordWeight(word, word,
                                source != null ? source : KeywordScorer.SOURCE_BODY,
                                (double) score,
                                frequency > 0 ? frequency : 1,
                                0));
                    }
                }
            }
        }

        if (filesById.isEmpty()) {
            return List.of();
        }

        // Query 2: sections + section keywords via JOIN
        Map<Long, SectionEntryBuilder> sectionsById = new LinkedHashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement("""
                SELECT s.file_id, s.id AS section_id, s.title, s.start_line, s.end_line,
                       sk.word, sk.score, sk.source, sk.frequency
                FROM sections s
                LEFT JOIN section_keywords sk ON sk.section_id = s.id
                JOIN files f ON s.file_id = f.id
                WHERE f.version = ?
                ORDER BY s.file_id, s.id, sk.score DESC
                """)) {
            stmt.setString(1, version);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long sectionId = rs.getLong("section_id");
                    long fileId = rs.getLong("file_id");
                    SectionEntryBuilder section = sectionsById.get(sectionId);
                    if (section == null) {
                        String title = rs.getString("title");
                        int start = rs.getInt("start_line");
                        int end = rs.getInt("end_line");
                        section = new SectionEntryBuilder(title, start, end);
                        sectionsById.put(sectionId, section);
                        filesById.get(fileId).sections.add(section);
                    }
                    String word = rs.getString("word");
                    if (word != null) {
                        int score = rs.getInt("score");
                        String source = rs.getString("source");
                        int frequency = rs.getInt("frequency");
                        section.keywords.add(new KeywordWeight(word, word,
                                source != null ? source : KeywordScorer.SOURCE_BODY,
                                (double) score,
                                frequency > 0 ? frequency : 1,
                                0));
                    }
                }
            }
        }

        return filesById.values().stream()
                .map(FileEntryBuilder::build)
                .toList();
    }

    /**
     * Builder for FileEntry to accumulate data during loading.
     */
    private static class FileEntryBuilder {
        final String path;
        final String extension;
        final List<KeywordWeight> keywords = new ArrayList<>();
        final List<SectionEntryBuilder> sections = new ArrayList<>();

        FileEntryBuilder(String path, String extension) {
            this.path = path;
            this.extension = extension;
        }

        FileEntry build() {
            return new FileEntry(
                    path,
                    extension,
                    List.copyOf(keywords),
                    sections.stream().map(SectionEntryBuilder::build).toList()
            );
        }
    }

    /**
     * Builder for SectionEntry to accumulate data during loading.
     */
    private static class SectionEntryBuilder {
        final String title;
        final int startLine;
        final int endLine;
        final List<KeywordWeight> keywords = new ArrayList<>();

        SectionEntryBuilder(String title, int startLine, int endLine) {
            this.title = title;
            this.startLine = startLine;
            this.endLine = endLine;
        }

        SectionEntry build() {
            return new SectionEntry(title, startLine, endLine, List.copyOf(keywords));
        }
    }
}
