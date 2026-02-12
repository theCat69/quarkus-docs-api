package com.fvd.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class FileUtilsTest {

    @Test
    void deleteDirectoryQuietlyWithNull() {
        assertThatCode(() -> FileUtils.deleteDirectoryQuietly(null))
                .doesNotThrowAnyException();
    }

    @Test
    void deleteDirectoryQuietlyWithNonExistentPath() {
        Path nonExistent = Path.of("/tmp/does-not-exist-" + System.nanoTime());
        assertThatCode(() -> FileUtils.deleteDirectoryQuietly(nonExistent))
                .doesNotThrowAnyException();
    }

    @Test
    void deleteDirectoryQuietlyDeletesEmptyDirectory(@TempDir Path tempDir) throws IOException {
        Path dir = tempDir.resolve("empty-dir");
        Files.createDirectories(dir);

        assertThat(Files.exists(dir)).isTrue();

        FileUtils.deleteDirectoryQuietly(dir);

        assertThat(Files.exists(dir)).isFalse();
    }

    @Test
    void deleteDirectoryQuietlyDeletesNestedDirectory(@TempDir Path tempDir) throws IOException {
        Path dir = tempDir.resolve("parent");
        Path subDir = dir.resolve("child");
        Path deepSubDir = subDir.resolve("grandchild");
        Files.createDirectories(deepSubDir);

        Files.writeString(dir.resolve("file1.txt"), "content1");
        Files.writeString(subDir.resolve("file2.txt"), "content2");
        Files.writeString(deepSubDir.resolve("file3.txt"), "content3");

        assertThat(Files.exists(dir)).isTrue();
        assertThat(Files.exists(deepSubDir.resolve("file3.txt"))).isTrue();

        FileUtils.deleteDirectoryQuietly(dir);

        assertThat(Files.exists(dir)).isFalse();
    }

    @Test
    void deleteDirectoryQuietlyDeletesDirectoryWithFiles(@TempDir Path tempDir) throws IOException {
        Path dir = tempDir.resolve("with-files");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("a.txt"), "aaa");
        Files.writeString(dir.resolve("b.txt"), "bbb");

        FileUtils.deleteDirectoryQuietly(dir);

        assertThat(Files.exists(dir)).isFalse();
    }

    @Test
    void deleteDirectoryQuietlyDoesNotAffectSiblingDirectories(@TempDir Path tempDir) throws IOException {
        Path dir1 = tempDir.resolve("dir1");
        Path dir2 = tempDir.resolve("dir2");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);
        Files.writeString(dir1.resolve("file.txt"), "content");
        Files.writeString(dir2.resolve("file.txt"), "content");

        FileUtils.deleteDirectoryQuietly(dir1);

        assertThat(Files.exists(dir1)).isFalse();
        assertThat(Files.exists(dir2)).isTrue();
        assertThat(Files.exists(dir2.resolve("file.txt"))).isTrue();
    }
}
