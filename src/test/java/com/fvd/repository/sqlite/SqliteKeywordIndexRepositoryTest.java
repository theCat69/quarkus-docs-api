package com.fvd.repository.sqlite;

import com.fvd.repository.domain.FileEntry;
import com.fvd.repository.domain.KeywordIndexData;
import com.fvd.repository.domain.KeywordWeight;
import com.fvd.repository.domain.SectionEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteKeywordIndexRepositoryTest {

    @TempDir
    Path tempDir;

    SqliteKeywordIndexRepository repository;

    @BeforeEach
    void setUp() {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db"));

        SqliteSchemaInitializerImpl initializer = new SqliteSchemaInitializerImpl(ds, tempDir.toString());
        initializer.initSchema();

        repository = new SqliteKeywordIndexRepository(ds);
    }

    @Test
    void findByVersionReturnsEmptyWhenNoData() {
        Optional<KeywordIndexData> result = repository.findByVersion("3.27");
        assertThat(result).isEmpty();
    }

    @Test
    void existsReturnsFalseWhenNoData() {
        assertThat(repository.exists("3.27")).isFalse();
    }

    @Test
    void saveAndFindByVersionRoundTrip() {
        KeywordIndexData data = buildTestData("3.27");

        repository.save("3.27", data);

        Optional<KeywordIndexData> result = repository.findByVersion("3.27");
        assertThat(result).isPresent();

        KeywordIndexData loaded = result.get();
        assertThat(loaded.version()).isEqualTo("3.27");
        assertThat(loaded.files()).hasSize(1);

        FileEntry file = loaded.files().get(0);
        assertThat(file.path()).isEqualTo("security-overview.adoc");
        assertThat(file.extension()).isEqualTo("quarkus-core");
        assertThat(file.keywords()).hasSize(2);

        // word() returns the stemmed field from KeywordWeight record
        // Verify source and frequency are preserved (this is the bug)
        KeywordWeight titleKw = file.keywords().stream()
                .filter(k -> k.word().equals("secur"))
                .findFirst().orElseThrow();
        assertThat(titleKw.source()).isEqualTo("title");
        assertThat(titleKw.frequency()).isEqualTo(3);

        KeywordWeight bodyKw = file.keywords().stream()
                .filter(k -> k.word().equals("overview"))
                .findFirst().orElseThrow();
        assertThat(bodyKw.source()).isEqualTo("body");
        assertThat(bodyKw.frequency()).isEqualTo(1);

        // Verify sections
        assertThat(file.sections()).hasSize(1);
        SectionEntry section = file.sections().get(0);
        assertThat(section.title()).isEqualTo("Getting Started");
        assertThat(section.startLine()).isEqualTo(1);
        assertThat(section.endLine()).isEqualTo(20);
        assertThat(section.keywords()).hasSize(1);

        KeywordWeight sectionKw = section.keywords().get(0);
        assertThat(sectionKw.word()).isEqualTo("start");
        assertThat(sectionKw.source()).isEqualTo("subtitle");
        assertThat(sectionKw.frequency()).isEqualTo(2);
    }

    @Test
    void existsReturnsTrueAfterSave() {
        KeywordIndexData data = new KeywordIndexData("3.27", List.of(
                new FileEntry("test.adoc", "quarkus-core", List.of(), List.of())
        ));
        repository.save("3.27", data);
        assertThat(repository.exists("3.27")).isTrue();
    }

    @Test
    void deleteByVersionRemovesData() {
        KeywordIndexData data = new KeywordIndexData("3.27", List.of(
                new FileEntry("test.adoc", "quarkus-core",
                        List.of(new KeywordWeight("test", 10)),
                        List.of())
        ));
        repository.save("3.27", data);
        assertThat(repository.findByVersion("3.27")).isPresent();

        repository.deleteByVersion("3.27");
        assertThat(repository.findByVersion("3.27")).isEmpty();
        assertThat(repository.exists("3.27")).isFalse();
    }

    @Test
    void saveOverwritesExistingVersion() {
        KeywordIndexData old = new KeywordIndexData("3.27", List.of(
                new FileEntry("old.adoc", "quarkus-core", List.of(), List.of())
        ));
        KeywordIndexData updated = new KeywordIndexData("3.27", List.of(
                new FileEntry("new.adoc", "quarkus-core", List.of(), List.of())
        ));

        repository.save("3.27", old);
        repository.save("3.27", updated);

        Optional<KeywordIndexData> result = repository.findByVersion("3.27");
        assertThat(result).isPresent();
        assertThat(result.get().files()).hasSize(1);
        assertThat(result.get().files().get(0).path()).isEqualTo("new.adoc");
    }

    private static KeywordIndexData buildTestData(String version) {
        return new KeywordIndexData(version, List.of(
                new FileEntry("security-overview.adoc", "quarkus-core",
                        List.of(new KeywordWeight("secur", "secur", "title", 50.0, 3, 0),
                                new KeywordWeight("overview", "overview", "body", 10.0, 1, 0)),
                        List.of(new SectionEntry("Getting Started", 1, 20,
                                List.of(new KeywordWeight("start", "start", "subtitle", 30.0, 2, 0)))))
        ));
    }
}
