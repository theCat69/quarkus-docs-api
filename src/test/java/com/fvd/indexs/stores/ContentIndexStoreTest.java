package com.fvd.indexs.stores;

import com.fvd.common.exceptions.InvalidInputException;
import com.fvd.indexs.indexers.ContentIndex;
import com.fvd.indexs.indexers.ContentOccurrence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentIndexStoreTest {

    @TempDir
    Path tempDir;

    private ContentIndexStore store;

    @BeforeEach
    void setUp() {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db"));
        SqliteSchemaInitializer initializer = new SqliteSchemaInitializer(ds);
        initializer.cacheDir = tempDir.toString();
        initializer.initSchema();
        store = new ContentIndexStore(ds);
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
        Map<String, List<ContentOccurrence>> occurrences = new HashMap<>();
        occurrences.put("security", List.of(
                new ContentOccurrence("security.adoc", 10, 1),
                new ContentOccurrence("security.adoc", 50, 3),
                new ContentOccurrence("config.adoc", 20, 2)
        ));
        occurrences.put("quarkus", List.of(
                new ContentOccurrence("security.adoc", 100, 5)
        ));
        ContentIndex index = new ContentIndex(occurrences);

        store.write("3.17", index);

        assertThat(store.exists("3.17")).isTrue();
        var result = store.read("3.17");
        assertThat(result).isPresent();
        ContentIndex loaded = result.get();

        assertThat(loaded.wordOccurrences).containsKey("security");
        assertThat(loaded.wordOccurrences).containsKey("quarkus");
        assertThat(loaded.wordOccurrences.get("security")).hasSize(3);
        assertThat(loaded.wordOccurrences.get("quarkus")).hasSize(1);

        // Verify occurrence details
        ContentOccurrence secOcc = loaded.wordOccurrences.get("security").stream()
                .filter(o -> o.charOffset == 10).findFirst().orElseThrow();
        assertThat(secOcc.filePath).isEqualTo("security.adoc");
        assertThat(secOcc.lineNumber).isEqualTo(1);
    }

    @Test
    void writeOverwritesExisting() {
        Map<String, List<ContentOccurrence>> first = new HashMap<>();
        first.put("old", List.of(new ContentOccurrence("file.adoc", 0, 1)));
        store.write("3.17", new ContentIndex(first));

        Map<String, List<ContentOccurrence>> second = new HashMap<>();
        second.put("new", List.of(new ContentOccurrence("file2.adoc", 5, 2)));
        store.write("3.17", new ContentIndex(second));

        var result = store.read("3.17");
        assertThat(result).isPresent();
        assertThat(result.get().wordOccurrences).containsKey("new");
        assertThat(result.get().wordOccurrences).doesNotContainKey("old");
    }

    @Test
    void readRejectsInvalidVersion() {
        assertThatThrownBy(() -> store.read("../etc"))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void writeRejectsInvalidVersion() {
        assertThatThrownBy(() -> store.write("../etc", new ContentIndex(Map.of())))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void deleteVersionRemovesData() {
        Map<String, List<ContentOccurrence>> occurrences = new HashMap<>();
        occurrences.put("word", List.of(new ContentOccurrence("file.adoc", 0, 1)));
        store.write("3.17", new ContentIndex(occurrences));
        assertThat(store.exists("3.17")).isTrue();

        store.deleteVersion("3.17");
        assertThat(store.exists("3.17")).isFalse();
        assertThat(store.read("3.17")).isEmpty();
    }

    @Test
    void writeHandlesEmptyIndex() {
        store.write("3.17", new ContentIndex(new HashMap<>()));
        assertThat(store.exists("3.17")).isFalse();
        assertThat(store.read("3.17")).isEmpty();
    }

    @Test
    void multipleVersionsAreIndependent() {
        Map<String, List<ContentOccurrence>> v1 = new HashMap<>();
        v1.put("security", List.of(new ContentOccurrence("sec.adoc", 0, 1)));
        store.write("3.17", new ContentIndex(v1));

        Map<String, List<ContentOccurrence>> v2 = new HashMap<>();
        v2.put("quarkus", List.of(new ContentOccurrence("q.adoc", 0, 1)));
        store.write("3.27", new ContentIndex(v2));

        var r1 = store.read("3.17");
        var r2 = store.read("3.27");
        assertThat(r1).isPresent();
        assertThat(r2).isPresent();
        assertThat(r1.get().wordOccurrences).containsKey("security");
        assertThat(r1.get().wordOccurrences).doesNotContainKey("quarkus");
        assertThat(r2.get().wordOccurrences).containsKey("quarkus");
        assertThat(r2.get().wordOccurrences).doesNotContainKey("security");
    }
}
