package com.fvd.repository.sqlite;

import com.fvd.common.validators.InputValidator;
import com.fvd.repository.api.SearchRepository;
import com.fvd.repository.domain.CodeSampleMatch;
import com.fvd.repository.domain.CodeSampleSearchQuery;
import com.fvd.repository.domain.FileMatch;
import com.fvd.repository.domain.FileSearchQuery;
import com.fvd.repository.domain.SearchResult;
import com.fvd.repository.domain.SectionMatch;
import com.fvd.repository.domain.SectionSearchQuery;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SQLite implementation of {@link SearchRepository}.
 * <p>
 * Performs search operations directly against SQLite tables using
 * keyword matching with scoring and pagination support.
 * </p>
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@LookupIfProperty(name = "app.database.type", stringValue = "sqlite", lookupIfMissing = true)
public class SqliteSearchRepository implements SearchRepository {

    private final DataSource dataSource;

    @Override
    public SearchResult<FileMatch> searchFiles(FileSearchQuery query) {
        InputValidator.validateVersion(query.version());

        if (query.keywords() == null || query.keywords().isEmpty()) {
            return SearchResult.empty();
        }

        try (Connection conn = dataSource.getConnection()) {
            return executeFileSearch(conn, query);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to search files for version: " + query.version(), e);
        }
    }

    @Override
    public SearchResult<SectionMatch> searchSections(SectionSearchQuery query) {
        InputValidator.validateVersion(query.version());

        if (query.keywords() == null || query.keywords().isEmpty()) {
            return SearchResult.empty();
        }

        try (Connection conn = dataSource.getConnection()) {
            return executeSectionSearch(conn, query);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to search sections for version: " + query.version(), e);
        }
    }

    @Override
    public SearchResult<CodeSampleMatch> searchCodeSamples(CodeSampleSearchQuery query) {
        InputValidator.validateVersion(query.version());

        if (query.keywords() == null || query.keywords().isEmpty()) {
            return SearchResult.empty();
        }

        try (Connection conn = dataSource.getConnection()) {
            return executeCodeSampleSearch(conn, query);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to search code samples for version: " + query.version(), e);
        }
    }

    private SearchResult<FileMatch> executeFileSearch(Connection conn, FileSearchQuery query) throws SQLException {
        List<String> keywords = query.keywords();
        String placeholders = SqlUtils.buildPlaceholders(keywords.size());

        StringBuilder sql = new StringBuilder();
        sql.append("""
                SELECT f.path, f.extension, SUM(fk.score) AS total_score, GROUP_CONCAT(DISTINCT fk.word) AS matched_words
                FROM files f
                JOIN file_keywords fk ON fk.file_id = f.id
                WHERE f.version = ?
                  AND fk.word IN (%s)
                """.formatted(placeholders));

        if (query.extension() != null && !query.extension().isBlank()) {
            sql.append(" AND f.extension = ?");
        }

        sql.append("""
                GROUP BY f.id, f.path, f.extension
                ORDER BY total_score DESC
                """);

        // Get total count first
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") AS subquery";
        int total;
        try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
            int idx = 1;
            countStmt.setString(idx++, query.version());
            for (String keyword : keywords) {
                countStmt.setString(idx++, keyword);
            }
            if (query.extension() != null && !query.extension().isBlank()) {
                countStmt.setString(idx, query.extension());
            }
            try (ResultSet rs = countStmt.executeQuery()) {
                rs.next();
                total = rs.getInt(1);
            }
        }

        if (total == 0) {
            return SearchResult.empty();
        }

        // Add pagination
        sql.append(" LIMIT ? OFFSET ?");

        List<FileMatch> results = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            stmt.setString(idx++, query.version());
            for (String keyword : keywords) {
                stmt.setString(idx++, keyword);
            }
            if (query.extension() != null && !query.extension().isBlank()) {
                stmt.setString(idx++, query.extension());
            }
            stmt.setInt(idx++, query.limit());
            stmt.setInt(idx, query.offset());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String path = rs.getString("path");
                    String extension = rs.getString("extension");
                    double score = rs.getDouble("total_score");
                    String matchedWordsStr = rs.getString("matched_words");
                    List<String> matchedKeywords = parseMatchedWords(matchedWordsStr);
                    results.add(new FileMatch(path, extension, score, matchedKeywords));
                }
            }
        }

        return new SearchResult<>(results, total);
    }

    private SearchResult<SectionMatch> executeSectionSearch(Connection conn, SectionSearchQuery query) throws SQLException {
        List<String> keywords = query.keywords();
        String placeholders = SqlUtils.buildPlaceholders(keywords.size());

        StringBuilder sql = new StringBuilder();
        sql.append("""
                SELECT f.path, f.extension, s.title, s.start_line, s.end_line,
                       SUM(sk.score) AS total_score, GROUP_CONCAT(DISTINCT sk.word) AS matched_words
                FROM sections s
                JOIN files f ON s.file_id = f.id
                JOIN section_keywords sk ON sk.section_id = s.id
                WHERE f.version = ?
                  AND sk.word IN (%s)
                """.formatted(placeholders));

        List<Object> params = new ArrayList<>();
        params.add(query.version());
        params.addAll(keywords);

        if (query.filePaths() != null && !query.filePaths().isEmpty()) {
            String filePathPlaceholders = SqlUtils.buildPlaceholders(query.filePaths().size());
            sql.append(" AND f.path IN (%s)".formatted(filePathPlaceholders));
            params.addAll(query.filePaths());
        }

        if (query.extension() != null && !query.extension().isBlank()) {
            sql.append(" AND f.extension = ?");
            params.add(query.extension());
        }

        if (query.sectionTitle() != null && !query.sectionTitle().isBlank()) {
            sql.append(" AND s.title LIKE ?");
            params.add("%" + query.sectionTitle() + "%");
        }

        sql.append("""
                GROUP BY f.id, f.path, f.extension, s.id, s.title, s.start_line, s.end_line
                ORDER BY total_score DESC
                """);

        // Get total count
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") AS subquery";
        int total;
        try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
            int idx = 1;
            for (Object param : params) {
                countStmt.setObject(idx++, param);
            }
            try (ResultSet rs = countStmt.executeQuery()) {
                rs.next();
                total = rs.getInt(1);
            }
        }

        if (total == 0) {
            return SearchResult.empty();
        }

        // Add pagination
        sql.append(" LIMIT ? OFFSET ?");
        params.add(query.limit());
        params.add(query.offset());

        List<SectionMatch> results = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            for (Object param : params) {
                stmt.setObject(idx++, param);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String path = rs.getString("path");
                    String sectionTitle = rs.getString("title");
                    int startLine = rs.getInt("start_line");
                    int endLine = rs.getInt("end_line");
                    String extension = rs.getString("extension");
                    double score = rs.getDouble("total_score");
                    String matchedWordsStr = rs.getString("matched_words");
                    List<String> matchedKeywords = parseMatchedWords(matchedWordsStr);
                    results.add(new SectionMatch(path, sectionTitle, startLine, endLine, extension, score, matchedKeywords));
                }
            }
        }

        return new SearchResult<>(results, total);
    }

    private SearchResult<CodeSampleMatch> executeCodeSampleSearch(Connection conn, CodeSampleSearchQuery query) throws SQLException {
        List<String> keywords = query.keywords();
        String placeholders = SqlUtils.buildPlaceholders(keywords.size());

        StringBuilder sql = new StringBuilder();
        sql.append("""
                SELECT cs.file_path, cs.section_title, cs.language, cs.content,
                       cs.start_line, cs.end_line, cs.extension,
                       SUM(csk.score) AS total_score, GROUP_CONCAT(DISTINCT csk.word) AS matched_words
                FROM code_samples cs
                JOIN code_sample_keywords csk ON csk.sample_id = cs.id
                WHERE cs.version = ?
                  AND csk.word IN (%s)
                """.formatted(placeholders));

        List<Object> params = new ArrayList<>();
        params.add(query.version());
        params.addAll(keywords);

        if (query.filePath() != null && !query.filePath().isBlank()) {
            sql.append(" AND cs.file_path = ?");
            params.add(query.filePath());
        }

        if (query.extension() != null && !query.extension().isBlank()) {
            sql.append(" AND cs.extension = ?");
            params.add(query.extension());
        }

        if (query.sectionTitle() != null && !query.sectionTitle().isBlank()) {
            sql.append(" AND cs.section_title LIKE ?");
            params.add("%" + query.sectionTitle() + "%");
        }

        sql.append("""
                GROUP BY cs.id, cs.file_path, cs.section_title, cs.language, cs.content, cs.start_line, cs.end_line, cs.extension
                ORDER BY total_score DESC
                """);

        // Get total count
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") AS subquery";
        int total;
        try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
            int idx = 1;
            for (Object param : params) {
                countStmt.setObject(idx++, param);
            }
            try (ResultSet rs = countStmt.executeQuery()) {
                rs.next();
                total = rs.getInt(1);
            }
        }

        if (total == 0) {
            return SearchResult.empty();
        }

        // Add pagination
        sql.append(" LIMIT ? OFFSET ?");
        params.add(query.limit());
        params.add(query.offset());

        List<CodeSampleMatch> results = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            for (Object param : params) {
                stmt.setObject(idx++, param);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String path = rs.getString("file_path");
                    String sectionTitle = rs.getString("section_title");
                    String language = rs.getString("language");
                    String content = rs.getString("content");
                    int startLine = rs.getInt("start_line");
                    int endLine = rs.getInt("end_line");
                    String extension = rs.getString("extension");
                    double score = rs.getDouble("total_score");
                    String matchedWordsStr = rs.getString("matched_words");
                    List<String> matchedKeywords = parseMatchedWords(matchedWordsStr);
                    results.add(new CodeSampleMatch(path, sectionTitle, language, content,
                            startLine, endLine, extension, score, matchedKeywords));
                }
            }
        }

        return new SearchResult<>(results, total);
    }

    private List<String> parseMatchedWords(String matchedWordsStr) {
        if (matchedWordsStr == null || matchedWordsStr.isBlank()) {
            return List.of();
        }
        Set<String> uniqueWords = new HashSet<>();
        for (String word : matchedWordsStr.split(",")) {
            String trimmed = word.trim();
            if (!trimmed.isEmpty()) {
                uniqueWords.add(trimmed);
            }
        }
        return List.copyOf(uniqueWords);
    }
}
