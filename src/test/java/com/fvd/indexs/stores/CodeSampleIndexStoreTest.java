package com.fvd.indexs.stores;

import com.fvd.common.exceptions.InvalidInputException;
import com.fvd.indexs.indexers.CodeSampleEntry;
import com.fvd.indexs.indexers.CodeSampleIndex;
import com.fvd.indexs.indexers.KeywordScore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class CodeSampleIndexStoreTest {

    @Inject
    CodeSampleIndexStore store;

    @Inject
    DataSource dataSource;

    @BeforeEach
    void cleanup() throws SQLException {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE files, file_keywords, sections, section_keywords, "
                + "code_samples, code_sample_keywords, github_index, document_metadata CASCADE");
        }
    }

    @Test
    void readReturnsEmptyWhenMissing() {
        assertThat(store.read("3.17")).isEmpty();
    }

    @Test
    void existsReturnsFalseWhenMissing() {
        assertThat(store.exists("3.17")).isFalse();
    }

    @Test
    void writeAndReadRoundTrip() {
        List<KeywordScore> keywords = List.of(
                new KeywordScore("inject", 8),
                new KeywordScore("bean", 5));
        CodeSampleEntry entry = new CodeSampleEntry(
                "security-oidc.adoc", "Configuration", "java",
                "import jakarta.inject.Inject;\n\n@Inject Bean bean;",
                10, 14, keywords);
        CodeSampleIndex index = new CodeSampleIndex(List.of(entry));

        store.write("3.17", index);

        assertThat(store.exists("3.17")).isTrue();
        var result = store.read("3.17");
        assertThat(result).isPresent();
        CodeSampleIndex loaded = result.get();
        assertThat(loaded.samples).hasSize(1);

        CodeSampleEntry loadedEntry = loaded.samples.get(0);
        assertThat(loadedEntry.filePath).isEqualTo("security-oidc.adoc");
        assertThat(loadedEntry.sectionTitle).isEqualTo("Configuration");
        assertThat(loadedEntry.language).isEqualTo("java");
        assertThat(loadedEntry.content).isEqualTo("import jakarta.inject.Inject;\n\n@Inject Bean bean;");
        assertThat(loadedEntry.startLine).isEqualTo(10);
        assertThat(loadedEntry.endLine).isEqualTo(14);
        assertThat(loadedEntry.keywords).hasSize(2);
        assertThat(loadedEntry.keywords.get(0).word).isEqualTo("inject");
        assertThat(loadedEntry.keywords.get(0).score).isEqualTo(8);
        assertThat(loadedEntry.keywords.get(1).word).isEqualTo("bean");
        assertThat(loadedEntry.keywords.get(1).score).isEqualTo(5);
    }

    @Test
    void writeOverwritesExisting() {
        CodeSampleEntry entry1 = new CodeSampleEntry(
                "file1.adoc", "Section A", "java", "code1", 1, 5,
                List.of(new KeywordScore("old", 1)));
        store.write("3.17", new CodeSampleIndex(List.of(entry1)));

        CodeSampleEntry entry2 = new CodeSampleEntry(
                "file2.adoc", "Section B", "xml", "code2", 10, 15,
                List.of(new KeywordScore("new", 2)));
        store.write("3.17", new CodeSampleIndex(List.of(entry2)));

        var result = store.read("3.17");
        assertThat(result).isPresent();
        assertThat(result.get().samples).hasSize(1);
        assertThat(result.get().samples.get(0).filePath).isEqualTo("file2.adoc");
        assertThat(result.get().samples.get(0).keywords.get(0).word).isEqualTo("new");
    }

    @Test
    void readRejectsInvalidVersion() {
        assertThatThrownBy(() -> store.read("../etc"))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void writeRejectsInvalidVersion() {
        assertThatThrownBy(() -> store.write("../etc", new CodeSampleIndex(List.of())))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void deleteVersionRemovesData() {
        CodeSampleEntry entry = new CodeSampleEntry(
                "file.adoc", "Section", "java", "code", 1, 5,
                List.of(new KeywordScore("word", 3)));
        store.write("3.17", new CodeSampleIndex(List.of(entry)));
        assertThat(store.exists("3.17")).isTrue();

        store.deleteVersion("3.17");
        assertThat(store.exists("3.17")).isFalse();
        assertThat(store.read("3.17")).isEmpty();
    }

    @Test
    void writeHandlesEntriesWithNoKeywords() {
        CodeSampleEntry entry = new CodeSampleEntry(
                "file.adoc", "Section", "yaml", "key: value", 1, 3,
                new ArrayList<>());
        store.write("3.17", new CodeSampleIndex(List.of(entry)));

        var result = store.read("3.17");
        assertThat(result).isPresent();
        assertThat(result.get().samples).hasSize(1);
        assertThat(result.get().samples.get(0).keywords).isEmpty();
    }

    @Test
    void writeHandlesMultipleEntries() {
        CodeSampleEntry entry1 = new CodeSampleEntry(
                "file1.adoc", "Sec A", "java", "code1", 1, 5,
                List.of(new KeywordScore("inject", 3)));
        CodeSampleEntry entry2 = new CodeSampleEntry(
                "file2.adoc", "Sec B", "xml", "code2", 10, 15,
                List.of(new KeywordScore("dependency", 2)));
        store.write("3.17", new CodeSampleIndex(List.of(entry1, entry2)));

        var result = store.read("3.17");
        assertThat(result).isPresent();
        assertThat(result.get().samples).hasSize(2);
        assertThat(result.get().samples.get(0).filePath).isEqualTo("file1.adoc");
        assertThat(result.get().samples.get(1).filePath).isEqualTo("file2.adoc");
    }
}
