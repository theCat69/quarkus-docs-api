package com.fvd;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZipDownloadServiceTest {

    @Test
    void extractRelativePathFindsAsciidocFiles() {
        ZipDownloadService service = new ZipDownloadService(null, null);
        String result = service.extractRelativePath(
                "quarkus-3.21/docs/src/main/asciidoc/security-oidc.adoc");
        assertThat(result).isEqualTo("security-oidc.adoc");
    }

    @Test
    void extractRelativePathHandlesNestedPaths() {
        ZipDownloadService service = new ZipDownloadService(null, null);
        String result = service.extractRelativePath(
                "quarkus-3.21/docs/src/main/asciidoc/guides/subdir/test.adoc");
        assertThat(result).isEqualTo("guides/subdir/test.adoc");
    }

    @Test
    void extractRelativePathReturnsNullForNonAsciidocFiles() {
        ZipDownloadService service = new ZipDownloadService(null, null);
        String result = service.extractRelativePath(
                "quarkus-3.21/pom.xml");
        assertThat(result).isNull();
    }

    @Test
    void extractRelativePathReturnsNullForDirectoryEntries() {
        ZipDownloadService service = new ZipDownloadService(null, null);
        String result = service.extractRelativePath(
                "quarkus-3.21/docs/src/main/asciidoc/");
        assertThat(result).isNull();
    }
}
