package com.fvd.api.services;

import com.fvd.cache.services.CacheService;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.subject.services.SubjectDeriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogServiceTest {

    @TempDir
    Path tempDir;

    private SubjectDeriver subjectDeriver;
    private KeywordIndexStore keywordIndexStore;
    private CacheService cacheService;
    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        subjectDeriver = mock(SubjectDeriver.class);
        keywordIndexStore = mock(KeywordIndexStore.class);
        cacheService = mock(CacheService.class);
        when(cacheService.versionDir("main")).thenReturn(tempDir.resolve("main"));
        when(cacheService.listCachedVersions()).thenReturn(List.of("main"));
        catalogService = new CatalogService(subjectDeriver, keywordIndexStore, cacheService);
    }

    @Test
    void extensionWithTitleFileGetsDescription() throws Exception {
        Path extDir = tempDir.resolve("main/docs/quarkiverse/quarkus-openapi-generator");
        Files.createDirectories(extDir);
        Files.writeString(extDir.resolve(".extension-title"), "Quarkus OpenAPI Generator");

        KeywordIndex index = new KeywordIndex();
        FileKeywordEntry entry = new FileKeywordEntry(
                "quarkiverse/quarkus-openapi-generator/index.adoc",
                List.of(new KeywordScore("openapi", 5)),
                List.of(),
                "quarkus-openapi-generator"
        );
        index.files = List.of(entry);
        when(keywordIndexStore.read("main")).thenReturn(Optional.of(index));
        when(subjectDeriver.deriveSubject(anyString())).thenReturn("misc");
        when(subjectDeriver.getAllSubjects()).thenReturn(List.of());

        var catalog = catalogService.getCatalog("main");

        assertThat(catalog.extensions).hasSize(1);
        assertThat(catalog.extensions.get(0).description).isEqualTo("Quarkus OpenAPI Generator");
    }

    @Test
    void extensionWithoutTitleFileGetsEmptyDescription() throws Exception {
        KeywordIndex index = new KeywordIndex();
        FileKeywordEntry entry = new FileKeywordEntry(
                "quarkiverse/my-ext/index.adoc",
                List.of(new KeywordScore("test", 5)),
                List.of(),
                "my-ext"
        );
        index.files = List.of(entry);
        when(keywordIndexStore.read("main")).thenReturn(Optional.of(index));
        when(subjectDeriver.deriveSubject(anyString())).thenReturn("misc");
        when(subjectDeriver.getAllSubjects()).thenReturn(List.of());

        var catalog = catalogService.getCatalog("main");

        assertThat(catalog.extensions).hasSize(1);
        assertThat(catalog.extensions.get(0).description).isEmpty();
    }

    @Test
    void quarkusCoreGetsHardcodedDescription() throws Exception {
        KeywordIndex index = new KeywordIndex();
        FileKeywordEntry entry = new FileKeywordEntry(
                "security-overview.adoc",
                List.of(new KeywordScore("security", 5)),
                List.of(),
                null
        );
        index.files = List.of(entry);
        when(keywordIndexStore.read("main")).thenReturn(Optional.of(index));
        when(subjectDeriver.deriveSubject(anyString())).thenReturn("misc");
        when(subjectDeriver.getAllSubjects()).thenReturn(List.of());

        var catalog = catalogService.getCatalog("main");

        assertThat(catalog.extensions).hasSize(1);
        assertThat(catalog.extensions.get(0).name).isEqualTo("quarkus-core");
        assertThat(catalog.extensions.get(0).description).isEqualTo("Core Quarkus framework documentation");
    }
}
