package com.fvd.search.services;

import com.fvd.asciidocs.parser.AsciidocParser;
import com.fvd.cache.services.CacheService;
import com.fvd.docs.exceptions.DocNotFoundException;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.services.ZipDownloadService;
import com.fvd.indexs.indexers.*;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SearchServiceTest {

    @TempDir
    Path tempDir;

    SearchService searchService;
    KeywordIndexStore keywordIndexStore;
    AsciidocParser asciidocParser;

    @BeforeEach
    void setUp() {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db"));
        SqliteSchemaInitializer initializer = new SqliteSchemaInitializer(ds);
        initializer.initSchema();
        keywordIndexStore = new KeywordIndexStore(ds);
        asciidocParser = new AsciidocParser();
        searchService = new SearchService(keywordIndexStore, null, null, null, asciidocParser);
    }

    private void seedIndex(String version, KeywordIndex index) {
        keywordIndexStore.write(version, index);
    }

    // --- File search tests ---

    @Test
    void searchFilesReturnsSortedByDescendingScore() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("low.adoc",
                        List.of(new KeywordScore("security", 5)), List.of()),
                new FileKeywordEntry("high.adoc",
                        List.of(new KeywordScore("security", 20)), List.of()),
                new FileKeywordEntry("mid.adoc",
                        List.of(new KeywordScore("security", 12)), List.of())
        ));
        seedIndex("3.21", index);

        List<FileSearchResult> results = searchService.searchFiles("3.21", List.of("security"));

        assertThat(results).hasSize(3);
        assertThat(results.get(0).path).isEqualTo("high.adoc");
        assertThat(results.get(0).score).isEqualTo(20.0);
        assertThat(results.get(1).path).isEqualTo("mid.adoc");
        assertThat(results.get(2).path).isEqualTo("low.adoc");
    }

    @Test
    void searchFilesAggregatesScoresAcrossKeywords() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("both.adoc",
                        List.of(new KeywordScore("security", 10), new KeywordScore("oidc", 8)), List.of()),
                new FileKeywordEntry("one.adoc",
                        List.of(new KeywordScore("security", 15)), List.of())
        ));
        seedIndex("3.21", index);

        List<FileSearchResult> results = searchService.searchFiles("3.21", List.of("security", "oidc"));

        // "both.adoc" matches both keywords: 10 + 8 + multi-keyword boost
        // "one.adoc" matches one keyword: 15
        assertThat(results).hasSize(2);
        assertThat(results.get(0).path).isEqualTo("both.adoc");
        assertThat(results.get(0).score).isGreaterThan(results.get(1).score);
    }

    @Test
    void searchFilesMultiKeywordBoostIncreasesScore() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("multi.adoc",
                        List.of(new KeywordScore("security", 5), new KeywordScore("oidc", 5)), List.of()),
                new FileKeywordEntry("single.adoc",
                        List.of(new KeywordScore("security", 10)), List.of())
        ));
        seedIndex("3.21", index);

        List<FileSearchResult> results = searchService.searchFiles("3.21", List.of("security", "oidc"));

        FileSearchResult multiResult = results.stream()
                .filter(r -> r.path.equals("multi.adoc")).findFirst().orElseThrow();
        FileSearchResult singleResult = results.stream()
                .filter(r -> r.path.equals("single.adoc")).findFirst().orElseThrow();
        assertThat(multiResult.score).isGreaterThan(singleResult.score);
    }

    @Test
    void searchFilesLimitsResultsToTen() {
        List<FileKeywordEntry> files = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            files.add(new FileKeywordEntry("file" + i + ".adoc",
                    List.of(new KeywordScore("test", 15 - i)), List.of()));
        }
        seedIndex("3.21", new KeywordIndex(files));

        List<FileSearchResult> results = searchService.searchFiles("3.21", List.of("test"));

        assertThat(results).hasSize(10);
        assertThat(results.get(0).path).isEqualTo("file0.adoc");
        assertThat(results.get(9).path).isEqualTo("file9.adoc");
    }

    @Test
    void searchFilesReturnsEmptyForUnknownKeyword() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc",
                        List.of(new KeywordScore("security", 10)), List.of())
        ));
        seedIndex("3.21", index);

        List<FileSearchResult> results = searchService.searchFiles("3.21", List.of("nonexistent"));

        assertThat(results).isEmpty();
    }

    @Test
    void searchFilesReturnsEmptyWhenNoIndexAndNoDeps() {
        List<FileSearchResult> results = searchService.searchFiles("3.21", List.of("security"));

        assertThat(results).isEmpty();
    }

    // --- Section search tests ---

    @Test
    void searchSectionsReturnsMatchingSections() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Overview", 1, 10,
                                List.of(new KeywordScore("security", 8))),
                        new SectionKeywordEntry("OIDC Config", 11, 30,
                                List.of(new KeywordScore("oidc", 12), new KeywordScore("security", 3)))
                ))
        ));
        seedIndex("3.21", index);

        List<SectionSearchResult> results = searchService.searchSections(
                "3.21", List.of("security"), List.of("security.adoc"));

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).section).isEqualTo("Overview"); // score 8 > 3
        assertThat(results.get(0).start).isEqualTo(1);
        assertThat(results.get(0).end).isEqualTo(10);
    }

    @Test
    void searchSectionsFiltersToProvidedFilePaths() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("included.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Included", 1, 10,
                                List.of(new KeywordScore("security", 5)))
                )),
                new FileKeywordEntry("excluded.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Excluded", 1, 10,
                                List.of(new KeywordScore("security", 20)))
                ))
        ));
        seedIndex("3.21", index);

        List<SectionSearchResult> results = searchService.searchSections(
                "3.21", List.of("security"), List.of("included.adoc"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).path).isEqualTo("included.adoc");
    }

    @Test
    void searchSectionsLimitsResultsToFive() {
        List<SectionKeywordEntry> sections = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            sections.add(new SectionKeywordEntry("Section " + i, i * 10 + 1, (i + 1) * 10,
                    List.of(new KeywordScore("test", 8 - i))));
        }
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("big.adoc", List.of(), sections)
        ));
        seedIndex("3.21", index);

        List<SectionSearchResult> results = searchService.searchSections(
                "3.21", List.of("test"), List.of("big.adoc"));

        assertThat(results).hasSize(5);
        assertThat(results.get(0).section).isEqualTo("Section 0"); // highest score
    }

    @Test
    void searchSectionsReturnsEmptyForUnmatchedKeyword() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Section", 1, 10,
                                List.of(new KeywordScore("security", 5)))
                ))
        ));
        seedIndex("3.21", index);

        List<SectionSearchResult> results = searchService.searchSections(
                "3.21", List.of("nonexistent"), List.of("test.adoc"));

        assertThat(results).isEmpty();
    }

    @Test
    void searchSectionsReturnsEmptyWhenNoIndexAndNoDeps() {
        List<SectionSearchResult> results = searchService.searchSections(
                "3.21", List.of("security"), List.of("test.adoc"));

        assertThat(results).isEmpty();
    }

    // --- Section content tests ---

    @Nested
    class SectionContentTests {

        private SearchService sectionSearchService;
        private DocStore realDocStore;

        @BeforeEach
        void setUpSectionContent() {
            CacheService cacheService = new CacheService(tempDir.toString());
            realDocStore = new DocStore(cacheService);
            sectionSearchService = new SearchService(
                    keywordIndexStore, null, null, realDocStore, asciidocParser);
        }

        @Test
        void getSectionContentReturnsMatchingSection() {
            String docContent = """
                    = Main Title
                    Some intro text.
                    
                    == Overview
                    This is the overview section.
                    It has multiple lines.
                    
                    == Configuration
                    Config details here.
                    """;
            realDocStore.write("3.21", "security.adoc", docContent);

            SectionContentResult result = sectionSearchService.getSectionContent(
                    "3.21", "security.adoc", "Overview");

            assertThat(result.path).isEqualTo("security.adoc");
            assertThat(result.title).isEqualTo("Overview");
            assertThat(result.content).contains("This is the overview section.");
            assertThat(result.content).contains("It has multiple lines.");
            assertThat(result.startLine).isGreaterThan(0);
            assertThat(result.endLine).isGreaterThanOrEqualTo(result.startLine);
        }

        @Test
        void getSectionContentIsCaseInsensitive() {
            String docContent = """
                    = Main Title
                    Intro.
                    
                    == Security Overview
                    Content here.
                    """;
            realDocStore.write("3.21", "security.adoc", docContent);

            SectionContentResult result = sectionSearchService.getSectionContent(
                    "3.21", "security.adoc", "security overview");

            assertThat(result.title).isEqualTo("Security Overview");
            assertThat(result.content).contains("Content here.");
        }

        @Test
        void getSectionContentThrowsWhenDocNotFound() {
            assertThatThrownBy(() ->
                    sectionSearchService.getSectionContent("3.21", "nonexistent.adoc", "Overview"))
                    .isInstanceOf(DocNotFoundException.class)
                    .hasMessageContaining("Document not found");
        }

        @Test
        void getSectionContentThrowsWhenSectionNotFound() {
            String docContent = """
                    = Main Title
                    Intro.
                    
                    == Overview
                    Content here.
                    """;
            realDocStore.write("3.21", "security.adoc", docContent);

            assertThatThrownBy(() ->
                    sectionSearchService.getSectionContent("3.21", "security.adoc", "Nonexistent Section"))
                    .isInstanceOf(DocNotFoundException.class)
                    .hasMessageContaining("Section not found");
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class LazyInitTests {

        @Mock
        private ZipDownloadService zipDownloadService;

        @Mock
        private KeywordIndexer keywordIndexer;

        @Mock
        private DocStore docStore;

        private SearchService lazySearchService;
        private KeywordIndexStore lazyKeywordIndexStore;

        @BeforeEach
        void setUpLazy() {
            SQLiteDataSource ds = new SQLiteDataSource();
            ds.setUrl("jdbc:sqlite:" + tempDir.resolve("lazy-test.db"));
            SqliteSchemaInitializer initializer = new SqliteSchemaInitializer(ds);
            initializer.initSchema();
            lazyKeywordIndexStore = new KeywordIndexStore(ds);
            lazySearchService = new SearchService(lazyKeywordIndexStore,
                    zipDownloadService, keywordIndexer, docStore, asciidocParser);
        }

        @Test
        void searchFilesTriggersDownloadWhenNoIndexAndNoCache() {
            when(docStore.docsExist("3.21")).thenReturn(false);
            when(zipDownloadService.streamAndExtract("3.21"))
                    .thenReturn(List.of("security.adoc", "config.adoc"));
            when(keywordIndexer.build(eq("3.21"), eq(List.of("security.adoc", "config.adoc"))))
                    .thenReturn(new KeywordIndex(List.of()));

            List<FileSearchResult> results = lazySearchService.searchFiles("3.21", List.of("security"));

            verify(zipDownloadService).streamAndExtract("3.21");
            verify(keywordIndexer).build("3.21", List.of("security.adoc", "config.adoc"));
            assertThat(results).isEmpty(); // no matching keywords in the empty index
        }

        @Test
        void searchFilesBuildsIndexFromExistingDocsWithoutDownload() {
            when(docStore.docsExist("3.21")).thenReturn(true);
            when(docStore.listDocFiles("3.21")).thenReturn(List.of("security.adoc"));
            when(keywordIndexer.build(eq("3.21"), eq(List.of("security.adoc"))))
                    .thenReturn(new KeywordIndex(List.of()));

            lazySearchService.searchFiles("3.21", List.of("security"));

            verify(zipDownloadService, never()).streamAndExtract("3.21");
            verify(keywordIndexer).build("3.21", List.of("security.adoc"));
        }

        @Test
        void searchFilesDoesNotTriggerDownloadWhenIndexExists() {
            // Pre-seed a keyword index so lazy init is not needed
            KeywordIndex index = new KeywordIndex(List.of(
                    new FileKeywordEntry("test.adoc",
                            List.of(new KeywordScore("security", 10)), List.of())
            ));
            lazyKeywordIndexStore.write("3.21", index);

            List<FileSearchResult> results = lazySearchService.searchFiles("3.21", List.of("security"));

            verify(zipDownloadService, never()).streamAndExtract("3.21");
            verify(keywordIndexer, never()).build(any(), any());
            assertThat(results).hasSize(1);
            assertThat(results.get(0).path).isEqualTo("test.adoc");
        }

        @Test
        void searchSectionsTriggersDownloadWhenNoIndexAndNoCache() {
            when(docStore.docsExist("3.21")).thenReturn(false);
            when(zipDownloadService.streamAndExtract("3.21"))
                    .thenReturn(List.of("security.adoc"));
            when(keywordIndexer.build(eq("3.21"), eq(List.of("security.adoc"))))
                    .thenReturn(new KeywordIndex(List.of()));

            List<SectionSearchResult> results = lazySearchService.searchSections(
                    "3.21", List.of("security"), List.of("security.adoc"));

            verify(zipDownloadService).streamAndExtract("3.21");
            verify(keywordIndexer).build("3.21", List.of("security.adoc"));
            assertThat(results).isEmpty();
        }

        @Test
        void searchSectionsDoesNotTriggerDownloadWhenIndexExists() {
            KeywordIndex index = new KeywordIndex(List.of(
                    new FileKeywordEntry("test.adoc", List.of(), List.of(
                            new SectionKeywordEntry("Section 1", 1, 10,
                                    List.of(new KeywordScore("security", 5)))
                    ))
            ));
            lazyKeywordIndexStore.write("3.21", index);

            List<SectionSearchResult> results = lazySearchService.searchSections(
                    "3.21", List.of("security"), List.of("test.adoc"));

            verify(zipDownloadService, never()).streamAndExtract("3.21");
            verify(keywordIndexer, never()).build(any(), any());
            assertThat(results).hasSize(1);
        }
    }
}
