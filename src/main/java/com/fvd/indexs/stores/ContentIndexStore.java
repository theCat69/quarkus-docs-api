package com.fvd.indexs.stores;

import com.fvd.common.validators.InputValidator;
import com.fvd.indexs.indexers.ContentIndex;
import com.fvd.indexs.indexers.ContentOccurrence;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ContentIndexStore {

    private final DataSource dataSource;

    public boolean exists(String version) {
        InputValidator.validateVersion(version);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT 1 FROM content_words WHERE version = ? LIMIT 1")) {
            stmt.setString(1, version);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check content index existence for version: " + version, e);
        }
    }

    public Optional<ContentIndex> read(String version) {
        InputValidator.validateVersion(version);

        try (Connection conn = dataSource.getConnection()) {
            Map<String, List<ContentOccurrence>> wordOccurrences = loadOccurrences(conn, version);
            if (wordOccurrences.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ContentIndex(wordOccurrences));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read content index for version: " + version, e);
        }
    }

    public void write(String version, ContentIndex index) {
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
            throw new RuntimeException("Failed to write content index for version: " + version, e);
        }
    }

    public void deleteVersion(String version) {
        InputValidator.validateVersion(version);

        try (Connection conn = dataSource.getConnection()) {
            deleteVersion(conn, version);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete content index for version: " + version, e);
        }
    }

    private void deleteVersion(Connection conn, String version) throws SQLException {
        // First delete positions (child rows), then words (parent rows)
        // We use a subquery to find word_ids for this version
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM content_word_positions WHERE word_id IN (SELECT id FROM content_words WHERE version = ?)")) {
            stmt.setString(1, version);
            stmt.executeUpdate();
        }
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM content_words WHERE version = ?")) {
            stmt.setString(1, version);
            stmt.executeUpdate();
        }
    }

    private void insertIndex(Connection conn, String version, ContentIndex index) throws SQLException {
        if (index.wordOccurrences == null || index.wordOccurrences.isEmpty()) {
            return;
        }

        try (PreparedStatement wordStmt = conn.prepareStatement(
                     "INSERT INTO content_words (version, word, file_path) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS);
             PreparedStatement posStmt = conn.prepareStatement(
                     "INSERT INTO content_word_positions (word_id, char_offset, line_number) VALUES (?, ?, ?)")) {

            for (Map.Entry<String, List<ContentOccurrence>> entry : index.wordOccurrences.entrySet()) {
                String word = entry.getKey();
                List<ContentOccurrence> occurrences = entry.getValue();

                // Group occurrences by file path
                Map<String, List<ContentOccurrence>> byFile = new LinkedHashMap<>();
                for (ContentOccurrence occ : occurrences) {
                    byFile.computeIfAbsent(occ.filePath, k -> new ArrayList<>()).add(occ);
                }

                for (Map.Entry<String, List<ContentOccurrence>> fileEntry : byFile.entrySet()) {
                    wordStmt.setString(1, version);
                    wordStmt.setString(2, word);
                    wordStmt.setString(3, fileEntry.getKey());
                    wordStmt.executeUpdate();

                    long wordId;
                    try (ResultSet keys = wordStmt.getGeneratedKeys()) {
                        keys.next();
                        wordId = keys.getLong(1);
                    }

                    for (ContentOccurrence occ : fileEntry.getValue()) {
                        posStmt.setLong(1, wordId);
                        posStmt.setInt(2, occ.charOffset);
                        posStmt.setInt(3, occ.lineNumber);
                        posStmt.addBatch();
                    }
                    posStmt.executeBatch();
                }
            }
        }
    }

    private Map<String, List<ContentOccurrence>> loadOccurrences(Connection conn, String version) throws SQLException {
        Map<String, List<ContentOccurrence>> result = new HashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement("""
                SELECT cw.word, cw.file_path, cwp.char_offset, cwp.line_number
                FROM content_words cw
                JOIN content_word_positions cwp ON cwp.word_id = cw.id
                WHERE cw.version = ?
                ORDER BY cw.word, cw.file_path, cwp.char_offset
                """)) {
            stmt.setString(1, version);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String word = rs.getString("word");
                    String filePath = rs.getString("file_path");
                    int charOffset = rs.getInt("char_offset");
                    int lineNumber = rs.getInt("line_number");
                    result.computeIfAbsent(word, k -> new ArrayList<>())
                            .add(new ContentOccurrence(filePath, charOffset, lineNumber));
                }
            }
        }

        return result;
    }
}
