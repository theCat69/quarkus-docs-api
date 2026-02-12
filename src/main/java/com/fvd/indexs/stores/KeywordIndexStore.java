package com.fvd.indexs.stores;

import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.indexs.indexers.SectionKeywordEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class KeywordIndexStore extends AbstractVersionedStore<KeywordIndex> {

    @Inject
    public KeywordIndexStore(DataSource dataSource) {
        super(dataSource);
    }

    /**
     * Protected no-arg constructor for Quarkus ARC proxy creation.
     */
    protected KeywordIndexStore() {
        super();
    }

    @Override
    protected String indexName() {
        return "keyword index";
    }

    @Override
    protected String existsQuery() {
        return "SELECT 1 FROM files WHERE version = ? LIMIT 1";
    }

    @Override
    protected Optional<KeywordIndex> doRead(Connection conn, String version) throws SQLException {
        List<FileKeywordEntry> fileEntries = loadFileEntries(conn, version);
        if (fileEntries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new KeywordIndex(fileEntries));
    }

    @Override
    protected void doDelete(Connection conn, String version) throws SQLException {
        // Due to ON DELETE CASCADE, deleting files also removes file_keywords, sections, section_keywords
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM files WHERE version = ?")) {
            stmt.setString(1, version);
            stmt.executeUpdate();
        }
    }

    @Override
    protected void doInsert(Connection conn, String version, KeywordIndex index) throws SQLException {
        if (index.files == null || index.files.isEmpty()) {
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

            for (FileKeywordEntry file : index.files) {
                fileStmt.setString(1, version);
                fileStmt.setString(2, file.path);
                fileStmt.setString(3, file.extension != null ? file.extension : "quarkus-core");
                fileStmt.executeUpdate();

                long fileId;
                try (ResultSet keys = fileStmt.getGeneratedKeys()) {
                    keys.next();
                    fileId = keys.getLong(1);
                }

                // Insert file-level keywords with source and frequency
                if (file.keywords != null) {
                    for (KeywordScore ks : file.keywords) {
                        fileKwStmt.setLong(1, fileId);
                        fileKwStmt.setString(2, ks.word);
                        fileKwStmt.setInt(3, ks.score);
                        fileKwStmt.setString(4, ks.source != null ? ks.source : "body");
                        fileKwStmt.setInt(5, ks.frequency > 0 ? ks.frequency : 1);
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
                                sectionKwStmt.setString(4, ks.source != null ? ks.source : "body");
                                sectionKwStmt.setInt(5, ks.frequency > 0 ? ks.frequency : 1);
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
                    FileKeywordEntry entry = filesById.get(fileId);
                    if (entry == null) {
                        String path = rs.getString("path");
                        String extension = rs.getString("extension");
                        entry = new FileKeywordEntry(path, new ArrayList<>(), new ArrayList<>(), extension);
                        filesById.put(fileId, entry);
                    }
                    String word = rs.getString("word");
                    if (word != null) {
                        int score = rs.getInt("score");
                        String source = rs.getString("source");
                        int frequency = rs.getInt("frequency");
                        entry.keywords.add(new KeywordScore(word, score,
                                source != null ? source : "body",
                                frequency > 0 ? frequency : 1));
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
                        String source = rs.getString("source");
                        int frequency = rs.getInt("frequency");
                        section.keywords.add(new KeywordScore(word, score,
                                source != null ? source : "body",
                                frequency > 0 ? frequency : 1));
                    }
                }
            }
        }

        return new ArrayList<>(filesById.values());
    }
}
