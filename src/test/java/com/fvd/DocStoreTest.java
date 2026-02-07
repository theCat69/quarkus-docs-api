package com.fvd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocStoreTest {

    @TempDir
    Path tempDir;

    DocStore docStore;

    @BeforeEach
    void setUp() {
        CacheService cacheService = new CacheService(tempDir.toString());
        docStore = new DocStore(cacheService);
    }

    @Test
    void readReturnsEmptyWhenMissing() {
        Optional<String> result = docStore.read("3.21", "security-oidc.adoc");
        assertThat(result).isEmpty();
    }

    @Test
    void writeAndReadRoundTrip() {
        String content = "= Security OIDC\nSome content here.";
        docStore.write("3.21", "security-oidc.adoc", content);
        Optional<String> result = docStore.read("3.21", "security-oidc.adoc");
        assertThat(result).isPresent().contains(content);
    }

    @Test
    void writeCreatesNestedDirectories() {
        String content = "= Guide";
        docStore.write("3.21", "guides/subdir/test.adoc", content);
        Optional<String> result = docStore.read("3.21", "guides/subdir/test.adoc");
        assertThat(result).isPresent().contains(content);
    }

    @Test
    void docsExistReturnsFalseWhenEmpty() {
        assertThat(docStore.docsExist("3.21")).isFalse();
    }

    @Test
    void docsExistReturnsTrueWhenDocPresent() {
        docStore.write("3.21", "test.adoc", "content");
        assertThat(docStore.docsExist("3.21")).isTrue();
    }

    @Test
    void readRejectsInvalidVersion() {
        assertThatThrownBy(() -> docStore.read("../etc", "test.adoc"))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void readRejectsPathTraversal() {
        assertThatThrownBy(() -> docStore.read("3.21", "../../etc/passwd"))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void writeRejectsInvalidVersion() {
        assertThatThrownBy(() -> docStore.write("../etc", "test.adoc", "content"))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void writeRejectsPathTraversal() {
        assertThatThrownBy(() -> docStore.write("3.21", "../../etc/passwd", "content"))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void listDocFilesReturnsEmptyWhenNoDocsDir() {
        List<String> result = docStore.listDocFiles("3.21");
        assertThat(result).isEmpty();
    }

    @Test
    void listDocFilesReturnsSortedRelativePaths() {
        docStore.write("3.21", "config.adoc", "config content");
        docStore.write("3.21", "security-overview.adoc", "security content");
        docStore.write("3.21", "cdi.adoc", "cdi content");

        List<String> result = docStore.listDocFiles("3.21");

        assertThat(result).containsExactly("cdi.adoc", "config.adoc", "security-overview.adoc");
    }

    @Test
    void listDocFilesIncludesNestedFiles() {
        docStore.write("3.21", "top.adoc", "top");
        docStore.write("3.21", "guides/nested.adoc", "nested");

        List<String> result = docStore.listDocFiles("3.21");

        assertThat(result).containsExactly("guides/nested.adoc", "top.adoc");
    }
}
