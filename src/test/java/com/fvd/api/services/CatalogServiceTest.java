package com.fvd.api.services;

import com.fvd.cache.services.CacheService;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.subject.services.MetadataAwareSubjectResolver;
import com.fvd.subject.services.SubjectDeriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogServiceTest {

    @TempDir
    Path tempDir;

    private SubjectDeriver subjectDeriver;
    private MetadataAwareSubjectResolver metadataResolver;
    private KeywordIndexStore keywordIndexStore;
    private CacheService cacheService;
    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        subjectDeriver = mock(SubjectDeriver.class);
        metadataResolver = mock(MetadataAwareSubjectResolver.class);
        keywordIndexStore = mock(KeywordIndexStore.class);
        cacheService = mock(CacheService.class);
        when(cacheService.versionDir("main")).thenReturn(tempDir.resolve("main"));
        when(cacheService.listCachedVersions()).thenReturn(List.of("main"));
        when(metadataResolver.loadMetadataMap(anyString())).thenReturn(Map.of());
        when(metadataResolver.resolveSubject(anyString(), any(Map.class))).thenReturn("misc");
        catalogService = new CatalogService(subjectDeriver, metadataResolver, keywordIndexStore, cacheService);
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
        when(subjectDeriver.getAllSubjects()).thenReturn(List.of());

        var catalog = catalogService.getCatalog("main");

        assertThat(catalog.extensions).hasSize(1);
        assertThat(catalog.extensions.get(0).name).isEqualTo("quarkus-core");
        assertThat(catalog.extensions.get(0).description).isEqualTo("Core Quarkus framework documentation");
    }

    @Test
    void extensionKeywordsAreAggregatedAndSortedByWeight() throws Exception {
        KeywordIndex index = new KeywordIndex();
        FileKeywordEntry file1 = new FileKeywordEntry(
                "quarkiverse/my-ext/page1.adoc",
                List.of(
                        new KeywordScore("secur", 10),
                        new KeywordScore("oidc", 8),
                        new KeywordScore("auth", 5)
                ),
                List.of(),
                "my-ext"
        );
        FileKeywordEntry file2 = new FileKeywordEntry(
                "quarkiverse/my-ext/page2.adoc",
                List.of(
                        new KeywordScore("secur", 6),
                        new KeywordScore("jwt", 4)
                ),
                List.of(),
                "my-ext"
        );
        index.files = List.of(file1, file2);
        when(keywordIndexStore.read("main")).thenReturn(Optional.of(index));
        when(subjectDeriver.getAllSubjects()).thenReturn(List.of());

        var catalog = catalogService.getCatalog("main");

        assertThat(catalog.extensions).hasSize(1);
        // secur: 10+6=16, oidc: 8, auth: 5, jwt: 4
        assertThat(catalog.extensions.get(0).keywords)
                .containsExactly("secur", "oidc", "auth", "jwt");
    }

    @Test
    void extensionWithNoKeywordsGetsEmptyList() throws Exception {
        KeywordIndex index = new KeywordIndex();
        FileKeywordEntry entry = new FileKeywordEntry(
                "quarkiverse/my-ext/index.adoc",
                List.of(),
                List.of(),
                "my-ext"
        );
        index.files = List.of(entry);
        when(keywordIndexStore.read("main")).thenReturn(Optional.of(index));
        when(subjectDeriver.getAllSubjects()).thenReturn(List.of());

        var catalog = catalogService.getCatalog("main");

        assertThat(catalog.extensions).hasSize(1);
        assertThat(catalog.extensions.get(0).keywords).isEmpty();
    }

    @Test
    void multipleExtensionsGetIndependentKeywords() throws Exception {
        KeywordIndex index = new KeywordIndex();
        FileKeywordEntry file1 = new FileKeywordEntry(
                "quarkiverse/ext-a/page.adoc",
                List.of(new KeywordScore("rest", 10)),
                List.of(),
                "ext-a"
        );
        FileKeywordEntry file2 = new FileKeywordEntry(
                "quarkiverse/ext-b/page.adoc",
                List.of(new KeywordScore("data", 8)),
                List.of(),
                "ext-b"
        );
        index.files = List.of(file1, file2);
        when(keywordIndexStore.read("main")).thenReturn(Optional.of(index));
        when(subjectDeriver.getAllSubjects()).thenReturn(List.of());

        var catalog = catalogService.getCatalog("main");

        assertThat(catalog.extensions).hasSize(2);
        var extA = catalog.extensions.stream().filter(e -> e.name.equals("ext-a")).findFirst().orElseThrow();
        var extB = catalog.extensions.stream().filter(e -> e.name.equals("ext-b")).findFirst().orElseThrow();
        assertThat(extA.keywords).containsExactly("rest");
        assertThat(extB.keywords).containsExactly("data");
    }

    @Test
    void keywordsAreLimitedToTopFifteen() throws Exception {
        KeywordIndex index = new KeywordIndex();
        List<KeywordScore> manyKeywords = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            manyKeywords.add(new KeywordScore("kw" + i, 20 - i));
        }
        FileKeywordEntry entry = new FileKeywordEntry(
                "quarkiverse/my-ext/index.adoc",
                manyKeywords,
                List.of(),
                "my-ext"
        );
        index.files = List.of(entry);
        when(keywordIndexStore.read("main")).thenReturn(Optional.of(index));
        when(subjectDeriver.getAllSubjects()).thenReturn(List.of());

        var catalog = catalogService.getCatalog("main");

        assertThat(catalog.extensions).hasSize(1);
        assertThat(catalog.extensions.get(0).keywords).hasSize(15);
        // Top 15 should be kw0 through kw14 (highest scores)
        assertThat(catalog.extensions.get(0).keywords.get(0)).isEqualTo("kw0");
        assertThat(catalog.extensions.get(0).keywords.get(14)).isEqualTo("kw14");
    }
}
