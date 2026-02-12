package com.fvd.repository.sqlite;

import com.fvd.common.validators.InputValidator;
import com.fvd.repository.api.CodeSampleIndexRepository;
import com.fvd.repository.domain.CodeSampleEntry;
import com.fvd.repository.domain.CodeSampleIndexData;
import com.fvd.repository.domain.KeywordWeight;
import com.fvd.repository.exceptions.RepositoryException;
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
 * SQLite implementation of {@link CodeSampleIndexRepository}.
 * <p>
 * Stores code sample index data in SQLite tables with support for
 * keyword associations and full-text search.
 * </p>
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@LookupIfProperty(name = "app.database.type", stringValue = "sqlite", lookupIfMissing = true)
public class SqliteCodeSampleIndexRepository implements CodeSampleIndexRepository {

    private final DataSource dataSource;

    @Override
    public boolean exists(String version) {
        InputValidator.validateVersion(version);

        try (Connection conn = dataSource.getConnection()) {
            return SqlUtils.existsByVersion(conn, "code_samples", "version", version);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to check code sample index existence for version: " + version, e);
        }
    }

    @Override
    public Optional<CodeSampleIndexData> findByVersion(String version) {
        InputValidator.validateVersion(version);

        try (Connection conn = dataSource.getConnection()) {
            List<CodeSampleEntry> entries = loadEntries(conn, version);
            if (entries.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new CodeSampleIndexData(version, entries));
        } catch (SQLException e) {
            throw new RepositoryException("Failed to read code sample index for version: " + version, e);
        }
    }

    @Override
    public void save(String version, CodeSampleIndexData data) {
        InputValidator.validateVersion(version);
        Objects.requireNonNull(data, "data must not be null");

        TransactionTemplate.executeInTransactionVoid(dataSource, conn -> {
            deleteVersion(conn, version);
            insertIndex(conn, version, data);
        }, "Failed to write code sample index for version: " + version);
    }

    @Override
    public void deleteByVersion(String version) {
        InputValidator.validateVersion(version);

        TransactionTemplate.executeInTransactionVoid(dataSource, conn -> {
            deleteVersion(conn, version);
        }, "Failed to delete code sample index for version: " + version);
    }

    private void deleteVersion(Connection conn, String version) throws SQLException {
        // Due to ON DELETE CASCADE, deleting code_samples also removes code_sample_keywords
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM code_samples WHERE version = ?")) {
            stmt.setString(1, version);
            stmt.executeUpdate();
        }
    }

    private void insertIndex(Connection conn, String version, CodeSampleIndexData data) throws SQLException {
        if (data.samples() == null || data.samples().isEmpty()) {
            return;
        }

        try (PreparedStatement sampleStmt = conn.prepareStatement(
                     "INSERT INTO code_samples (version, file_path, section_title, language, content, start_line, end_line, extension) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS);
             PreparedStatement kwStmt = conn.prepareStatement(
                     "INSERT INTO code_sample_keywords (sample_id, word, score) VALUES (?, ?, ?)")) {

            for (CodeSampleEntry sample : data.samples()) {
                sampleStmt.setString(1, version);
                sampleStmt.setString(2, sample.filePath());
                sampleStmt.setString(3, sample.sectionTitle());
                sampleStmt.setString(4, sample.language());
                sampleStmt.setString(5, sample.content());
                sampleStmt.setInt(6, sample.startLine());
                sampleStmt.setInt(7, sample.endLine());
                sampleStmt.setString(8, sample.extension() != null ? sample.extension() : "quarkus-core");
                sampleStmt.executeUpdate();

                long sampleId;
                try (ResultSet keys = sampleStmt.getGeneratedKeys()) {
                    keys.next();
                    sampleId = keys.getLong(1);
                }

                if (sample.keywords() != null) {
                    for (KeywordWeight kw : sample.keywords()) {
                        kwStmt.setLong(1, sampleId);
                        kwStmt.setString(2, kw.word());
                        kwStmt.setInt(3, (int) kw.weight());
                        kwStmt.addBatch();
                    }
                    kwStmt.executeBatch();
                }
            }
        }
    }

    private List<CodeSampleEntry> loadEntries(Connection conn, String version) throws SQLException {
        Map<Long, CodeSampleEntryBuilder> entriesById = new LinkedHashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement("""
                SELECT cs.id AS sample_id, cs.file_path, cs.section_title, cs.language,
                       cs.content, cs.start_line, cs.end_line, cs.extension,
                       csk.word, csk.score
                FROM code_samples cs
                LEFT JOIN code_sample_keywords csk ON csk.sample_id = cs.id
                WHERE cs.version = ?
                ORDER BY cs.id, csk.score DESC
                """)) {
            stmt.setString(1, version);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long sampleId = rs.getLong("sample_id");
                    CodeSampleEntryBuilder builder = entriesById.get(sampleId);
                    if (builder == null) {
                        builder = new CodeSampleEntryBuilder(
                                rs.getString("file_path"),
                                rs.getString("section_title"),
                                rs.getString("language"),
                                rs.getString("content"),
                                rs.getInt("start_line"),
                                rs.getInt("end_line"),
                                rs.getString("extension"));
                        entriesById.put(sampleId, builder);
                    }
                    String word = rs.getString("word");
                    if (word != null) {
                        int score = rs.getInt("score");
                        builder.keywords.add(new KeywordWeight(word, score));
                    }
                }
            }
        }

        return entriesById.values().stream()
                .map(CodeSampleEntryBuilder::build)
                .toList();
    }

    /**
     * Builder for CodeSampleEntry to accumulate data during loading.
     */
    private static class CodeSampleEntryBuilder {
        final String filePath;
        final String sectionTitle;
        final String language;
        final String content;
        final int startLine;
        final int endLine;
        final String extension;
        final List<KeywordWeight> keywords = new ArrayList<>();

        CodeSampleEntryBuilder(String filePath, String sectionTitle, String language,
                               String content, int startLine, int endLine, String extension) {
            this.filePath = filePath;
            this.sectionTitle = sectionTitle;
            this.language = language;
            this.content = content;
            this.startLine = startLine;
            this.endLine = endLine;
            this.extension = extension;
        }

        CodeSampleEntry build() {
            return new CodeSampleEntry(
                    filePath, sectionTitle, language, content,
                    startLine, endLine, List.copyOf(keywords), extension
            );
        }
    }
}
