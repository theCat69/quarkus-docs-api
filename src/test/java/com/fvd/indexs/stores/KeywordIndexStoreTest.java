package com.fvd.indexs.stores;

import com.fvd.common.exceptions.InvalidInputException;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.indexs.indexers.SectionKeywordEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeywordIndexStoreTest {

    @TempDir
    Path tempDir;

    KeywordIndexStore keywordIndexStore;

    @BeforeEach
    void setUp() {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db"));
        SqliteSchemaInitializer initializer = new SqliteSchemaInitializer(ds);
        initializer.initSchema();
        keywordIndexStore = new KeywordIndexStore(ds);
    }

    @Test
    void readReturnsEmptyWhenMissing() {
        Optional<KeywordIndex> result = keywordIndexStore.read("3.27");
        assertThat(result).isEmpty();
    }

    @Test
    void writeAndReadRoundTrip() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc",
                        List.of(new KeywordScore("security", 10)),
                        List.of(new SectionKeywordEntry("Overview", 1, 10,
                                List.of(new KeywordScore("overview", 5)))))
        ));
        keywordIndexStore.write("3.27", index);
        Optional<KeywordIndex> result = keywordIndexStore.read("3.27");
        assertThat(result).isPresent();
        assertThat(result.get().files).hasSize(1);
        assertThat(result.get().files.get(0).path).isEqualTo("test.adoc");
        assertThat(result.get().files.get(0).keywords).hasSize(1);
        assertThat(result.get().files.get(0).keywords.get(0).word).isEqualTo("security");
        assertThat(result.get().files.get(0).keywords.get(0).score).isEqualTo(10);
        assertThat(result.get().files.get(0).sections).hasSize(1);
        assertThat(result.get().files.get(0).sections.get(0).title).isEqualTo("Overview");
        assertThat(result.get().files.get(0).sections.get(0).keywords.get(0).word).isEqualTo("overview");
    }

    @Test
    void writeOverwritesExisting() {
        KeywordIndex oldIndex = new KeywordIndex(List.of(
                new FileKeywordEntry("old.adoc", List.of(), List.of())));
        KeywordIndex newIndex = new KeywordIndex(List.of(
                new FileKeywordEntry("new.adoc", List.of(), List.of())));
        keywordIndexStore.write("3.27", oldIndex);
        keywordIndexStore.write("3.27", newIndex);
        Optional<KeywordIndex> result = keywordIndexStore.read("3.27");
        assertThat(result).isPresent();
        assertThat(result.get().files).hasSize(1);
        assertThat(result.get().files.get(0).path).isEqualTo("new.adoc");
    }

    @Test
    void readRejectsInvalidVersion() {
        assertThatThrownBy(() -> keywordIndexStore.read("../etc"))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void writeRejectsInvalidVersion() {
        KeywordIndex index = new KeywordIndex(List.of());
        assertThatThrownBy(() -> keywordIndexStore.write("../etc", index))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void deleteVersionRemovesData() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc", List.of(), List.of())));
        keywordIndexStore.write("3.27", index);
        assertThat(keywordIndexStore.read("3.27")).isPresent();

        keywordIndexStore.deleteVersion("3.27");
        assertThat(keywordIndexStore.read("3.27")).isEmpty();
    }
}
