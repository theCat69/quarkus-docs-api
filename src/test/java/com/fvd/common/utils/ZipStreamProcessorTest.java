package com.fvd.common.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ZipStreamProcessorTest {

    @Test
    void processEntriesFiltersMatchingEntries() throws IOException {
        byte[] zipBytes = createZip(
                "docs/security.adoc", "security content",
                "docs/config.adoc", "config content",
                "readme.md", "readme content"
        );

        List<String> processedNames = new ArrayList<>();
        List<String> processedContents = new ArrayList<>();

        ZipStreamProcessor.processEntries(
                new ByteArrayInputStream(zipBytes),
                name -> name.endsWith(".adoc"),
                (name, bytes) -> {
                    processedNames.add(name);
                    processedContents.add(new String(bytes));
                }
        );

        assertThat(processedNames).containsExactly("docs/security.adoc", "docs/config.adoc");
        assertThat(processedContents).containsExactly("security content", "config content");
    }

    @Test
    void processEntriesSkipsDirectories() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add a directory entry
            ZipEntry dirEntry = new ZipEntry("docs/");
            zos.putNextEntry(dirEntry);
            zos.closeEntry();

            // Add a file entry
            ZipEntry fileEntry = new ZipEntry("docs/file.txt");
            zos.putNextEntry(fileEntry);
            zos.write("file content".getBytes());
            zos.closeEntry();
        }

        List<String> processedNames = new ArrayList<>();

        ZipStreamProcessor.processEntries(
                new ByteArrayInputStream(baos.toByteArray()),
                name -> true,
                (name, bytes) -> processedNames.add(name)
        );

        assertThat(processedNames).containsExactly("docs/file.txt");
    }

    @Test
    void processEntriesReadsContentCorrectly() throws IOException {
        String expectedContent = "Hello, this is test content with special chars: \u00e9\u00e8\u00ea";
        byte[] zipBytes = createZip("test.txt", expectedContent);

        List<String> contents = new ArrayList<>();

        ZipStreamProcessor.processEntries(
                new ByteArrayInputStream(zipBytes),
                name -> true,
                (name, bytes) -> contents.add(new String(bytes))
        );

        assertThat(contents).hasSize(1);
        assertThat(contents.get(0)).isEqualTo(expectedContent);
    }

    @Test
    void processEntriesWithNoMatchingFilter() throws IOException {
        byte[] zipBytes = createZip("file.txt", "content");

        List<String> processedNames = new ArrayList<>();

        ZipStreamProcessor.processEntries(
                new ByteArrayInputStream(zipBytes),
                name -> name.endsWith(".adoc"),
                (name, bytes) -> processedNames.add(name)
        );

        assertThat(processedNames).isEmpty();
    }

    @Test
    void processEntriesWithEmptyZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Empty zip
        }

        List<String> processedNames = new ArrayList<>();

        ZipStreamProcessor.processEntries(
                new ByteArrayInputStream(baos.toByteArray()),
                name -> true,
                (name, bytes) -> processedNames.add(name)
        );

        assertThat(processedNames).isEmpty();
    }

    @Test
    void processEntriesWithMultipleMatchingEntries() throws IOException {
        byte[] zipBytes = createZip(
                "a.adoc", "aaa",
                "b.adoc", "bbb",
                "c.adoc", "ccc"
        );

        List<String> processedNames = new ArrayList<>();

        ZipStreamProcessor.processEntries(
                new ByteArrayInputStream(zipBytes),
                name -> true,
                (name, bytes) -> processedNames.add(name)
        );

        assertThat(processedNames).containsExactly("a.adoc", "b.adoc", "c.adoc");
    }

    /**
     * Helper to create a zip archive in memory with the given name/content pairs.
     */
    private byte[] createZip(String... nameContentPairs) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < nameContentPairs.length; i += 2) {
                String name = nameContentPairs[i];
                String content = nameContentPairs[i + 1];
                ZipEntry entry = new ZipEntry(name);
                zos.putNextEntry(entry);
                zos.write(content.getBytes());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
