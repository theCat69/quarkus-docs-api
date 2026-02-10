package com.fvd.github.services;

import com.fvd.asciidocs.parser.AsciidocParser;
import com.fvd.cache.services.CacheService;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.exceptions.UpstreamException;
import com.fvd.search.TestSearchConfig;
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
    private GitHubService gitHubService;

    private DocStore docStore;
    private CacheService cacheService;
    private ZipDownloadService service;
    private final DocParser docParser = new AsciidocParser(new TestSearchConfig());

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        cacheService = new CacheService(tempDir.toString());
        docStore = new DocStore(cacheService);
        service = new ZipDownloadService(gitHubService, docStore, cacheService, docParser);
    }

    // -- extractRelativePath tests (existing) --

    @Test
    void extractRelativePathFindsAsciidocFiles() {
        String result = service.extractRelativePath(
                "quarkusio.github.io-main/_versions/3.27/guides/security-oidc.adoc", "3.27");
        assertThat(result).isEqualTo("security-oidc.adoc");
    }

    @Test
    void extractRelativePathHandlesNestedPaths() {
        String result = service.extractRelativePath(
                "quarkusio.github.io-main/_versions/3.27/guides/subdir/test.adoc", "3.27");
        assertThat(result).isEqualTo("subdir/test.adoc");
    }

    @Test
    void extractRelativePathReturnsNullForNonAsciidocFiles() {
        String result = service.extractRelativePath(
                "quarkusio.github.io-main/pom.xml", "3.27");
        assertThat(result).isNull();
    }

    @Test
    void extractRelativePathReturnsNullForDirectoryEntries() {
        String result = service.extractRelativePath(
                "quarkusio.github.io-main/_versions/3.27/guides/", "3.27");
        assertThat(result).isNull();
    }

    // -- streamAndExtract tests --

    @Test
    void streamAndExtractExtractsAsciidocFiles() throws IOException {
        byte[] zip = buildZip(
                entry("quarkusio.github.io-main/_versions/3.27/guides/security.adoc", "= Security"),
                entry("quarkusio.github.io-main/_versions/3.27/guides/config.adoc", "= Config"),
                entry("quarkusio.github.io-main/pom.xml", "<project/>")
        );
        when(gitHubService.fetchZipStream()).thenReturn(new ByteArrayInputStream(zip));

        List<String> extracted = service.streamAndExtract("3.27");

        assertThat(extracted).containsExactlyInAnyOrder("security.adoc", "config.adoc");
        assertThat(docStore.read("3.27", "security.adoc")).hasValue("= Security");
        assertThat(docStore.read("3.27", "config.adoc")).hasValue("= Config");
    }

    @Test
    void streamAndExtractReturnsEmptyListWhenNoAsciidocFiles() throws IOException {
        byte[] zip = buildZip(
                entry("quarkusio.github.io-main/pom.xml", "<project/>"),
                entry("quarkusio.github.io-main/src/main/java/Foo.java", "class Foo {}")
        );
        when(gitHubService.fetchZipStream()).thenReturn(new ByteArrayInputStream(zip));

        List<String> extracted = service.streamAndExtract("3.27");

        assertThat(extracted).isEmpty();
    }

    @Test
    void streamAndExtractHandlesNestedAsciidocPaths() throws IOException {
        byte[] zip = buildZip(
                entry("quarkusio.github.io-main/_versions/3.27/guides/sub/deep.adoc", "= Deep Guide")
        );
        when(gitHubService.fetchZipStream()).thenReturn(new ByteArrayInputStream(zip));

        List<String> extracted = service.streamAndExtract("3.27");

        assertThat(extracted).containsExactly("sub/deep.adoc");
        assertThat(docStore.read("3.27", "sub/deep.adoc")).hasValue("= Deep Guide");
    }

    @Test
    void streamAndExtractDoesNotCorruptExistingCacheOnFailure() throws IOException {
        // Pre-populate cache with a valid file
        docStore.write("3.27", "existing.adoc", "= Existing content");

        // Simulate a failure during zip streaming
        InputStream brokenStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Simulated stream failure");
            }
        };
        when(gitHubService.fetchZipStream()).thenReturn(brokenStream);

        assertThatThrownBy(() -> service.streamAndExtract("3.27"))
                .isInstanceOf(UpstreamException.class);

        // Existing cache should be untouched
        assertThat(docStore.read("3.27", "existing.adoc")).hasValue("= Existing content");
    }

    @Test
    void streamAndExtractWritesToCacheOnlyOnSuccess() throws IOException {
        byte[] zip = buildZip(
                entry("quarkusio.github.io-main/_versions/3.27/guides/new-file.adoc", "= New content")
        );
        when(gitHubService.fetchZipStream()).thenReturn(new ByteArrayInputStream(zip));

        service.streamAndExtract("3.27");

        assertThat(docStore.read("3.27", "new-file.adoc")).hasValue("= New content");
        // Staging directory should be cleaned up
        Path stagingDir = cacheService.versionDir("3.27").resolve("docs-staging");
        assertThat(Files.exists(stagingDir)).isFalse();
    }

    @Test
    void streamAndExtractCleansStagingDirOnFailure() throws IOException {
        // Pre-populate cache with a valid file
        docStore.write("3.27", "existing.adoc", "= Existing");

        // Build a partial zip that will fail mid-stream by throwing during read
        when(gitHubService.fetchZipStream()).thenReturn(new InputStream() {
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

        assertThatThrownBy(() -> service.streamAndExtract("3.27"))
                .isInstanceOf(UpstreamException.class);

        // Staging directory should be cleaned up even on failure
        Path stagingDir = cacheService.versionDir("3.27").resolve("docs-staging");
        assertThat(Files.exists(stagingDir)).isFalse();
        // Existing cache should be untouched
        assertThat(docStore.read("3.27", "existing.adoc")).hasValue("= Existing");
    }

    @Test
    void streamAndExtractAllExtractsMultipleVersions() throws IOException {
        byte[] zip = buildZip(
                entry("quarkusio.github.io-main/_versions/3.21/guides/security.adoc", "= Security 3.21"),
                entry("quarkusio.github.io-main/_versions/3.27/guides/security.adoc", "= Security 3.27"),
                entry("quarkusio.github.io-main/_versions/3.27/guides/config.adoc", "= Config 3.27"),
                entry("quarkusio.github.io-main/_versions/main/guides/other.adoc", "= Other main")
        );
        when(gitHubService.fetchZipStream()).thenReturn(new ByteArrayInputStream(zip));

        var result = service.streamAndExtractAll(List.of("3.21", "3.27"));

        assertThat(result).containsOnlyKeys("3.21", "3.27");
        assertThat(result.get("3.21")).containsExactly("security.adoc");
        assertThat(result.get("3.27")).containsExactlyInAnyOrder("security.adoc", "config.adoc");
        assertThat(docStore.read("3.21", "security.adoc")).hasValue("= Security 3.21");
        assertThat(docStore.read("3.27", "security.adoc")).hasValue("= Security 3.27");
        // main version was not requested, should not be extracted
        assertThat(docStore.read("main", "other.adoc")).isEmpty();
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
