package com.fvd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZipDownloadServiceTest {

    @Mock
    private GitHubClient gitHubClient;

    private DocStore docStore;
    private CacheService cacheService;
    private ZipDownloadService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        cacheService = new CacheService(tempDir.toString());
        docStore = new DocStore(cacheService);
        service = new ZipDownloadService(gitHubClient, docStore, cacheService);
    }

    // -- extractRelativePath tests (existing) --

    @Test
    void extractRelativePathFindsAsciidocFiles() {
        String result = service.extractRelativePath(
                "quarkus-3.21/docs/src/main/asciidoc/security-oidc.adoc");
        assertThat(result).isEqualTo("security-oidc.adoc");
    }

    @Test
    void extractRelativePathHandlesNestedPaths() {
        String result = service.extractRelativePath(
                "quarkus-3.21/docs/src/main/asciidoc/guides/subdir/test.adoc");
        assertThat(result).isEqualTo("guides/subdir/test.adoc");
    }

    @Test
    void extractRelativePathReturnsNullForNonAsciidocFiles() {
        String result = service.extractRelativePath(
                "quarkus-3.21/pom.xml");
        assertThat(result).isNull();
    }

    @Test
    void extractRelativePathReturnsNullForDirectoryEntries() {
        String result = service.extractRelativePath(
                "quarkus-3.21/docs/src/main/asciidoc/");
        assertThat(result).isNull();
    }

    // -- streamAndExtract tests --

    @Test
    void streamAndExtractExtractsAsciidocFiles() throws IOException {
        byte[] zip = buildZip(
                entry("quarkus-3.21/docs/src/main/asciidoc/security.adoc", "= Security"),
                entry("quarkus-3.21/docs/src/main/asciidoc/config.adoc", "= Config"),
                entry("quarkus-3.21/pom.xml", "<project/>")
        );
        when(gitHubClient.fetchZipStream("3.21")).thenReturn(new ByteArrayInputStream(zip));

        List<String> extracted = service.streamAndExtract("3.21");

        assertThat(extracted).containsExactlyInAnyOrder("security.adoc", "config.adoc");
        assertThat(docStore.read("3.21", "security.adoc")).hasValue("= Security");
        assertThat(docStore.read("3.21", "config.adoc")).hasValue("= Config");
    }

    @Test
    void streamAndExtractReturnsEmptyListWhenNoAsciidocFiles() throws IOException {
        byte[] zip = buildZip(
                entry("quarkus-3.21/pom.xml", "<project/>"),
                entry("quarkus-3.21/src/main/java/Foo.java", "class Foo {}")
        );
        when(gitHubClient.fetchZipStream("3.21")).thenReturn(new ByteArrayInputStream(zip));

        List<String> extracted = service.streamAndExtract("3.21");

        assertThat(extracted).isEmpty();
    }

    @Test
    void streamAndExtractHandlesNestedAsciidocPaths() throws IOException {
        byte[] zip = buildZip(
                entry("quarkus-3.21/docs/src/main/asciidoc/guides/sub/deep.adoc", "= Deep Guide")
        );
        when(gitHubClient.fetchZipStream("3.21")).thenReturn(new ByteArrayInputStream(zip));

        List<String> extracted = service.streamAndExtract("3.21");

        assertThat(extracted).containsExactly("guides/sub/deep.adoc");
        assertThat(docStore.read("3.21", "guides/sub/deep.adoc")).hasValue("= Deep Guide");
    }

    @Test
    void streamAndExtractDoesNotCorruptExistingCacheOnFailure() throws IOException {
        // Pre-populate cache with a valid file
        docStore.write("3.21", "existing.adoc", "= Existing content");

        // Simulate a failure during zip streaming
        InputStream brokenStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Simulated stream failure");
            }
        };
        when(gitHubClient.fetchZipStream("3.21")).thenReturn(brokenStream);

        assertThatThrownBy(() -> service.streamAndExtract("3.21"))
                .isInstanceOf(UpstreamException.class);

        // Existing cache should be untouched
        assertThat(docStore.read("3.21", "existing.adoc")).hasValue("= Existing content");
    }

    @Test
    void streamAndExtractWritesToCacheOnlyOnSuccess() throws IOException {
        byte[] zip = buildZip(
                entry("quarkus-3.21/docs/src/main/asciidoc/new-file.adoc", "= New content")
        );
        when(gitHubClient.fetchZipStream("3.21")).thenReturn(new ByteArrayInputStream(zip));

        service.streamAndExtract("3.21");

        assertThat(docStore.read("3.21", "new-file.adoc")).hasValue("= New content");
        // Staging directory should be cleaned up
        Path stagingDir = cacheService.versionDir("3.21").resolve("docs-staging");
        assertThat(Files.exists(stagingDir)).isFalse();
    }

    @Test
    void streamAndExtractCleansStagingDirOnFailure() throws IOException {
        // Pre-populate cache with a valid file
        docStore.write("3.21", "existing.adoc", "= Existing");

        // Build a partial zip that will fail mid-stream by throwing during read
        when(gitHubClient.fetchZipStream("3.21")).thenReturn(new InputStream() {
            private final byte[] corruptData = new byte[]{0x50, 0x4b, 0x03, 0x04}; // ZIP magic bytes
            private int pos = 0;
            @Override
            public int read() throws IOException {
                if (pos < corruptData.length) {
                    return corruptData[pos++] & 0xFF;
                }
                throw new IOException("Simulated mid-stream failure");
            }
        });

        assertThatThrownBy(() -> service.streamAndExtract("3.21"))
                .isInstanceOf(UpstreamException.class);

        // Staging directory should be cleaned up even on failure
        Path stagingDir = cacheService.versionDir("3.21").resolve("docs-staging");
        assertThat(Files.exists(stagingDir)).isFalse();
        // Existing cache should be untouched
        assertThat(docStore.read("3.21", "existing.adoc")).hasValue("= Existing");
    }

    // -- Helpers --

    private record ZipFileEntry(String path, String content) {}

    private ZipFileEntry entry(String path, String content) {
        return new ZipFileEntry(path, content);
    }

    private byte[] buildZip(ZipFileEntry... entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (ZipFileEntry entry : entries) {
                zos.putNextEntry(new ZipEntry(entry.path()));
                zos.write(entry.content().getBytes());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
