package com.fvd.indexs.stores;

import com.fvd.common.validators.InputValidator;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.indexs.indexers.SectionKeywordEntry;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class KeywordIndexStore {

    private final DataSource dataSource;

    public boolean exists(String version) {
        InputValidator.validateVersion(version);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT 1 FROM files WHERE version = ? LIMIT 1")) {
            stmt.setString(1, version);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check keyword index existence for version: " + version, e);
        }
    }

    public Optional<KeywordIndex> read(String version) {
        InputValidator.validateVersion(version);

        try (Connection conn = dataSource.getConnection()) {
            List<FileKeywordEntry> fileEntries = loadFileEntries(conn, version);
            if (fileEntries.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new KeywordIndex(fileEntries));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read keyword index for version: " + version, e);
        }
    }

    public void write(String version, KeywordIndex index) {
        InputValidator.validateVersion(version);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                deleteVersion(conn, version);
                insertIndex(conn, version, index);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to write keyword index for version: " + version, e);
        }
    }

    public void deleteVersion(String version) {
        InputValidator.validateVersion(version);

        try (Connection conn = dataSource.getConnection()) {
            deleteVersion(conn, version);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete keyword index for version: " + version, e);
        }
    }

    private void deleteVersion(Connection conn, String version) throws SQLException {
        // Due to ON DELETE CASCADE, deleting files also removes file_keywords, sections, section_keywords
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM files WHERE version = ?")) {
            stmt.setString(1, version);
            stmt.executeUpdate();
        }
    }

    private void insertIndex(Connection conn, String version, KeywordIndex index) throws SQLException {
        if (index.files == null || index.files.isEmpty()) {
            return;
        }

        try (PreparedStatement fileStmt = conn.prepareStatement(
                     "INSERT INTO files (version, path) VALUES (?, ?)",
                     Statement.RETURN_GENERATED_KEYS);
             PreparedStatement fileKwStmt = conn.prepareStatement(
                     "INSERT INTO file_keywords (file_id, word, score) VALUES (?, ?, ?)");
             PreparedStatement sectionStmt = conn.prepareStatement(
                     "INSERT INTO sections (file_id, title, start_line, end_line) VALUES (?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS);
             PreparedStatement sectionKwStmt = conn.prepareStatement(
                     "INSERT INTO section_keywords (section_id, word, score) VALUES (?, ?, ?)")) {

            for (FileKeywordEntry file : index.files) {
                fileStmt.setString(1, version);
                fileStmt.setString(2, file.path);
                fileStmt.executeUpdate();

                long fileId;
                try (ResultSet keys = fileStmt.getGeneratedKeys()) {
                    keys.next();
                    fileId = keys.getLong(1);
                }

                // Insert file-level keywords
                if (file.keywords != null) {
                    for (KeywordScore ks : file.keywords) {
                        fileKwStmt.setLong(1, fileId);
                        fileKwStmt.setString(2, ks.word);
                        fileKwStmt.setInt(3, ks.score);
                        fileKwStmt.addBatch();
                    }
                    fileKwStmt.executeBatch();
                }

                // Insert sections and section keywords
                if (file.sections != null) {
                    for (SectionKeywordEntry section : file.sections) {
                        sectionStmt.setLong(1, fileId);
                        sectionStmt.setString(2, section.title);
                        sectionStmt.setInt(3, section.start);
                        sectionStmt.setInt(4, section.end);
                        sectionStmt.executeUpdate();

                        long sectionId;
                        try (ResultSet keys = sectionStmt.getGeneratedKeys()) {
                            keys.next();
                            sectionId = keys.getLong(1);
                        }

                        if (section.keywords != null) {
                            for (KeywordScore ks : section.keywords) {
                                sectionKwStmt.setLong(1, sectionId);
                                sectionKwStmt.setString(2, ks.word);
                                sectionKwStmt.setInt(3, ks.score);
                                sectionKwStmt.addBatch();
                            }
                            sectionKwStmt.executeBatch();
                        }
                    }
                }
            }
        }
    }

    private List<FileKeywordEntry> loadFileEntries(Connection conn, String version) throws SQLException {
        // Use LinkedHashMap to preserve insertion order (by file id)
        Map<Long, FileKeywordEntry> filesById = new LinkedHashMap<>();

        // Query 1: files + file keywords via JOIN
        try (PreparedStatement stmt = conn.prepareStatement("""
                SELECT f.id AS file_id, f.path, fk.word, fk.score
                FROM files f
                LEFT JOIN file_keywords fk ON fk.file_id = f.id
                WHERE f.version = ?
                ORDER BY f.id, fk.score DESC
                """)) {
            stmt.setString(1, version);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long fileId = rs.getLong("file_id");
                    FileKeywordEntry entry = filesById.get(fileId);
                    if (entry == null) {
                        String path = rs.getString("path");
                        entry = new FileKeywordEntry(path, new ArrayList<>(), new ArrayList<>());
                        filesById.put(fileId, entry);
                    }
                    String word = rs.getString("word");
                    if (word != null) {
                        int score = rs.getInt("score");
                        entry.keywords.add(new KeywordScore(word, score));
                    }
                }
            }
        }

        if (filesById.isEmpty()) {
            return List.of();
        }

        // Query 2: sections + section keywords via JOIN
        Map<Long, SectionKeywordEntry> sectionsById = new LinkedHashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement("""
                SELECT s.file_id, s.id AS section_id, s.title, s.start_line, s.end_line,
                       sk.word, sk.score
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
                    SectionKeywordEntry section = sectionsById.get(sectionId);
                    if (section == null) {
                        String title = rs.getString("title");
                        int start = rs.getInt("start_line");
                        int end = rs.getInt("end_line");
                        section = new SectionKeywordEntry(title, start, end, new ArrayList<>());
                        sectionsById.put(sectionId, section);
                        filesById.get(fileId).sections.add(section);
                    }
                    String word = rs.getString("word");
                    if (word != null) {
                        int score = rs.getInt("score");
                        section.keywords.add(new KeywordScore(word, score));
                    }
                }
            }
        }

        return new ArrayList<>(filesById.values());
    }
}
