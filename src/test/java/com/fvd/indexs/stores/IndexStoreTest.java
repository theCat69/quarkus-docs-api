package com.fvd.indexs.stores;

import com.fvd.common.exceptions.InvalidInputException;
import com.fvd.github.clients.GithubApiIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexStoreTest {

    @TempDir
    Path tempDir;

    IndexStore indexStore;

    @BeforeEach
    void setUp() {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db"));
        SqliteSchemaInitializer initializer = new SqliteSchemaInitializer(ds);
        initializer.initSchema();
        indexStore = new IndexStore(ds);
    }

    @Test
    void readReturnsEmptyWhenMissing() {
        Optional<List<GithubApiIndex>> result = indexStore.read("3.21");
        assertThat(result).isEmpty();
    }

    @Test
    void writeAndReadRoundTrip() {
        List<GithubApiIndex> index = List.of(
                new GithubApiIndex("test.adoc", "path/test.adoc", "sha1"));
        indexStore.write("3.21", index);
        Optional<List<GithubApiIndex>> result = indexStore.read("3.21");
        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
        assertThat(result.get().get(0).name).isEqualTo("test.adoc");
        assertThat(result.get().get(0).sha).isEqualTo("sha1");
    }

    @Test
    void writeOverwritesExisting() {
        List<GithubApiIndex> oldIndex = List.of(
                new GithubApiIndex("old.adoc", "path/old.adoc", "old-sha"));
        List<GithubApiIndex> newIndex = List.of(
                new GithubApiIndex("new.adoc", "path/new.adoc", "new-sha"));
        indexStore.write("3.21", oldIndex);
        indexStore.write("3.21", newIndex);
        Optional<List<GithubApiIndex>> result = indexStore.read("3.21");
        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
        assertThat(result.get().get(0).name).isEqualTo("new.adoc");
    }

    @Test
    void readRejectsInvalidVersion() {
        assertThatThrownBy(() -> indexStore.read("../etc"))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void writeRejectsInvalidVersion() {
        assertThatThrownBy(() -> indexStore.write("../etc", List.of()))
                .isInstanceOf(InvalidInputException.class);
    }
}
