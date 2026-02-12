package com.fvd.indexs.stores;

import com.fvd.indexs.indexers.CodeSampleEntry;
import com.fvd.indexs.indexers.CodeSampleIndex;
import com.fvd.indexs.indexers.KeywordScore;
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
public class CodeSampleIndexStore extends AbstractVersionedStore<CodeSampleIndex> {

    @Inject
    public CodeSampleIndexStore(DataSource dataSource) {
        super(dataSource);
    }

    /**
     * Protected no-arg constructor for Quarkus ARC proxy creation.
     */
    protected CodeSampleIndexStore() {
        super();
    }

    @Override
    protected String indexName() {
        return "code sample index";
    }

    @Override
    protected String existsQuery() {
        return "SELECT 1 FROM code_samples WHERE version = ? LIMIT 1";
    }

    @Override
    protected Optional<CodeSampleIndex> doRead(Connection conn, String version) throws SQLException {
        List<CodeSampleEntry> entries = loadEntries(conn, version);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CodeSampleIndex(entries));
    }

    @Override
    protected void doDelete(Connection conn, String version) throws SQLException {
        // Due to ON DELETE CASCADE, deleting code_samples also removes code_sample_keywords
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM code_samples WHERE version = ?")) {
            stmt.setString(1, version);
            stmt.executeUpdate();
        }
    }

    @Override
    protected void doInsert(Connection conn, String version, CodeSampleIndex index) throws SQLException {
        if (index.samples == null || index.samples.isEmpty()) {
            return;
        }

        try (PreparedStatement sampleStmt = conn.prepareStatement(
                     "INSERT INTO code_samples (version, file_path, section_title, language, content, start_line, end_line, extension) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS);
             PreparedStatement kwStmt = conn.prepareStatement(
                     "INSERT INTO code_sample_keywords (sample_id, word, score) VALUES (?, ?, ?)")) {

            for (CodeSampleEntry sample : index.samples) {
                sampleStmt.setString(1, version);
                sampleStmt.setString(2, sample.filePath);
                sampleStmt.setString(3, sample.sectionTitle);
                sampleStmt.setString(4, sample.language);
                sampleStmt.setString(5, sample.content);
                sampleStmt.setInt(6, sample.startLine);
                sampleStmt.setInt(7, sample.endLine);
                sampleStmt.setString(8, sample.extension != null ? sample.extension : "quarkus-core");
                sampleStmt.executeUpdate();

                long sampleId;
                try (ResultSet keys = sampleStmt.getGeneratedKeys()) {
                    keys.next();
                    sampleId = keys.getLong(1);
                }

                if (sample.keywords != null) {
                    for (KeywordScore ks : sample.keywords) {
                        kwStmt.setLong(1, sampleId);
                        kwStmt.setString(2, ks.word);
                        kwStmt.setInt(3, ks.score);
                        kwStmt.addBatch();
                    }
                    kwStmt.executeBatch();
                }
            }
        }
    }

    private List<CodeSampleEntry> loadEntries(Connection conn, String version) throws SQLException {
        Map<Long, CodeSampleEntry> entriesById = new LinkedHashMap<>();

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
                    CodeSampleEntry entry = entriesById.get(sampleId);
                    if (entry == null) {
                        entry = new CodeSampleEntry(
                                rs.getString("file_path"),
                                rs.getString("section_title"),
                                rs.getString("language"),
                                rs.getString("content"),
                                rs.getInt("start_line"),
                                rs.getInt("end_line"),
                                new ArrayList<>(),
                                rs.getString("extension"));
                        entriesById.put(sampleId, entry);
                    }
                    String word = rs.getString("word");
                    if (word != null) {
                        int score = rs.getInt("score");
                        entry.keywords.add(new KeywordScore(word, score));
                    }
                }
            }
        }

        return new ArrayList<>(entriesById.values());
    }
}
