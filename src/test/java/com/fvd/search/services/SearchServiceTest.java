package com.fvd.search.services;

import com.fvd.asciidocs.parser.AsciidocParser;
import com.fvd.cache.services.CacheService;
import com.fvd.common.matchers.FuzzyMatcher;
import com.fvd.docs.exceptions.DocNotFoundException;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.services.ZipDownloadService;
import com.fvd.indexs.indexers.*;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import com.fvd.search.SearchConfig;
import com.fvd.search.TestSearchConfig;
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
    CodeSampleIndexStore codeSampleIndexStore;
    DocParser docParser;
    CacheService cacheService;

    @BeforeEach
    void setUp() {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db"));
        SqliteSchemaInitializer initializer = new SqliteSchemaInitializer(ds);
        initializer.initSchema();
        keywordIndexStore = new KeywordIndexStore(ds);
        codeSampleIndexStore = new CodeSampleIndexStore(ds);
        docParser = new AsciidocParser(new TestSearchConfig());
        cacheService = new CacheService(tempDir.toString());
        SearchConfig searchConfig = new TestSearchConfig();
        FuzzyMatcher fuzzyMatcher = new FuzzyMatcher(searchConfig);
        searchService = new SearchService(keywordIndexStore, codeSampleIndexStore, null, null, null, docParser, cacheService, searchConfig, fuzzyMatcher);
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
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("security"), 10, 0);

        assertThat(result.items()).hasSize(3);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.items().get(0).path).isEqualTo("high.adoc");
        assertThat(result.items().get(0).score).isEqualTo(20.0);
        assertThat(result.items().get(1).path).isEqualTo("mid.adoc");
        assertThat(result.items().get(2).path).isEqualTo("low.adoc");
    }

    @Test
    void searchFilesAggregatesScoresAcrossKeywords() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("both.adoc",
                        List.of(new KeywordScore("security", 10), new KeywordScore("oidc", 8)), List.of()),
                new FileKeywordEntry("one.adoc",
                        List.of(new KeywordScore("security", 15)), List.of())
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("security", "oidc"), 10, 0);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).path).isEqualTo("both.adoc");
        assertThat(result.items().get(0).score).isGreaterThan(result.items().get(1).score);
    }

    @Test
    void searchFilesMultiKeywordBoostIncreasesScore() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("multi.adoc",
                        List.of(new KeywordScore("security", 5), new KeywordScore("oidc", 5)), List.of()),
                new FileKeywordEntry("single.adoc",
                        List.of(new KeywordScore("security", 10)), List.of())
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("security", "oidc"), 10, 0);

        FileSearchResult multiResult = result.items().stream()
                .filter(r -> r.path.equals("multi.adoc")).findFirst().orElseThrow();
        FileSearchResult singleResult = result.items().stream()
                .filter(r -> r.path.equals("single.adoc")).findFirst().orElseThrow();
        assertThat(multiResult.score).isGreaterThan(singleResult.score);
    }

    @Test
    void searchFilesPaginationLimitsResults() {
        List<FileKeywordEntry> files = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            files.add(new FileKeywordEntry("file" + i + ".adoc",
                    List.of(new KeywordScore("test", 15 - i)), List.of()));
        }
        seedIndex("3.27", new KeywordIndex(files));

        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("test"), 5, 0);

        assertThat(result.items()).hasSize(5);
        assertThat(result.total()).isEqualTo(15);
        assertThat(result.items().get(0).path).isEqualTo("file0.adoc");
        assertThat(result.items().get(4).path).isEqualTo("file4.adoc");
    }

    @Test
    void searchFilesPaginationWithOffset() {
        List<FileKeywordEntry> files = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            files.add(new FileKeywordEntry("file" + i + ".adoc",
                    List.of(new KeywordScore("test", 15 - i)), List.of()));
        }
        seedIndex("3.27", new KeywordIndex(files));

        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("test"), 5, 5);

        assertThat(result.items()).hasSize(5);
        assertThat(result.total()).isEqualTo(15);
        assertThat(result.items().get(0).path).isEqualTo("file5.adoc");
        assertThat(result.items().get(4).path).isEqualTo("file9.adoc");
    }

    @Test
    void searchFilesPaginationOffsetBeyondResults() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc",
                        List.of(new KeywordScore("security", 10)), List.of())
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("security"), 10, 100);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void searchFilesReturnsEmptyForUnknownKeyword() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc",
                        List.of(new KeywordScore("security", 10)), List.of())
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("nonexistent"), 10, 0);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualTo(0);
    }

    @Test
    void searchFilesReturnsEmptyWhenNoIndexAndNoDeps() {
        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("security"), 10, 0);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualTo(0);
    }

    // --- Prefix matching tests ---

    @Test
    void searchFilesPrefixMatchReturnsResults() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc",
                        List.of(new KeywordScore("security", 10)), List.of())
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("secur"), 10, 0);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).path).isEqualTo("security.adoc");
    }

    @Test
    void searchFilesPrefixMatchDoesNotMatchNonPrefix() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("obscure.adoc",
                        List.of(new KeywordScore("obscure", 10)), List.of())
        ));
        seedIndex("3.27", index);

        // "secur" should NOT match "obscure"
        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("secur"), 10, 0);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void searchFilesPrefixMatchAppliesDiscount() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc",
                        List.of(new KeywordScore("security", 10)), List.of())
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> exactResult = searchService.searchFiles("3.27", List.of("security"), 10, 0);
        PaginatedResult<FileSearchResult> prefixResult = searchService.searchFiles("3.27", List.of("secur"), 10, 0);

        // Exact match: score = 10.0, Prefix match: score = 10.0 * 0.8 = 8.0
        assertThat(exactResult.items().get(0).score).isEqualTo(10.0);
        assertThat(prefixResult.items().get(0).score).isEqualTo(8.0);
    }

    @Test
    void searchFilesExactMatchTakesPrecedenceOverPrefix() {
        // "security" is both exact match for query "security" and prefix match
        // Exact should win with full score
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc",
                        List.of(new KeywordScore("security", 10)), List.of())
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("security"), 10, 0);

        assertThat(result.items().get(0).score).isEqualTo(10.0); // full score, not discounted
    }

    @Test
    void searchFilesQueryKeywordLongerThanIndexedDoesNotMatch() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc",
                        List.of(new KeywordScore("sec", 10)), List.of())
        ));
        seedIndex("3.27", index);

        // "security" is longer than "sec", so "sec".startsWith("security") is false
        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("security"), 10, 0);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void searchSectionsPrefixMatchReturnsResults() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Auth Section", 1, 10,
                                List.of(new KeywordScore("authentication", 8)))
                ))
        ));
        seedIndex("3.27", index);

        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("auth"), null, 10, 0);

        assertThat(result.items()).hasSize(1);
        // Prefix discount: 8 * 0.8 = 6.4
        assertThat(result.items().get(0).score).isEqualTo(6.4);
    }

    @Test
    void searchCodeSamplesPrefixMatchReturnsResults() {
        CodeSampleIndex index = new CodeSampleIndex(List.of(
                new CodeSampleEntry("test.adoc", "Section A", "java", "code1", 1, 5,
                        List.of(new KeywordScore("security", 10)))
        ));
        codeSampleIndexStore.write("3.27", index);

        PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                "3.27", List.of("secur"), null, null, 10, 0);

        assertThat(result.items()).hasSize(1);
        // Prefix discount: 10 * 0.8 = 8.0
        assertThat(result.items().get(0).score).isEqualTo(8.0);
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
        seedIndex("3.27", index);

        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("security"), List.of("security.adoc"), 10, 0);

        assertThat(result.items()).isNotEmpty();
        assertThat(result.items().get(0).section).isEqualTo("Overview"); // score 8 > 3
        assertThat(result.items().get(0).start).isEqualTo(1);
        assertThat(result.items().get(0).end).isEqualTo(10);
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
        seedIndex("3.27", index);

        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("security"), List.of("included.adoc"), 10, 0);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).path).isEqualTo("included.adoc");
    }

    @Test
    void searchSectionsPaginationLimitsResults() {
        List<SectionKeywordEntry> sections = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            sections.add(new SectionKeywordEntry("Section " + i, i * 10 + 1, (i + 1) * 10,
                    List.of(new KeywordScore("test", 8 - i))));
        }
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("big.adoc", List.of(), sections)
        ));
        seedIndex("3.27", index);

        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("test"), List.of("big.adoc"), 3, 0);

        assertThat(result.items()).hasSize(3);
        assertThat(result.total()).isEqualTo(8);
        assertThat(result.items().get(0).section).isEqualTo("Section 0"); // highest score
    }

    @Test
    void searchSectionsPaginationWithOffset() {
        List<SectionKeywordEntry> sections = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            sections.add(new SectionKeywordEntry("Section " + i, i * 10 + 1, (i + 1) * 10,
                    List.of(new KeywordScore("test", 8 - i))));
        }
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("big.adoc", List.of(), sections)
        ));
        seedIndex("3.27", index);

        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("test"), List.of("big.adoc"), 3, 3);

        assertThat(result.items()).hasSize(3);
        assertThat(result.total()).isEqualTo(8);
        assertThat(result.items().get(0).section).isEqualTo("Section 3");
    }

    @Test
    void searchSectionsReturnsEmptyForUnmatchedKeyword() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Section", 1, 10,
                                List.of(new KeywordScore("security", 5)))
                ))
        ));
        seedIndex("3.27", index);

        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("nonexistent"), List.of("test.adoc"), 10, 0);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void searchSectionsReturnsEmptyWhenNoIndexAndNoDeps() {
        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("security"), List.of("test.adoc"), 10, 0);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void searchSectionsAppliesMultiKeywordBoostWhenBothMatch() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Both Keywords", 1, 10,
                                List.of(new KeywordScore("security", 5), new KeywordScore("oidc", 5)))
                ))
        ));
        seedIndex("3.27", index);

        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("security", "oidc"), null, 10, 0);

        assertThat(result.items()).hasSize(1);
        // Raw sum is 10, with 1.5x boost should be 15
        assertThat(result.items().get(0).score).isEqualTo(15.0);
    }

    @Test
    void searchSectionsDoesNotApplyBoostWhenOnlyOneKeywordMatches() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc", List.of(), List.of(
                        new SectionKeywordEntry("One Match", 1, 10,
                                List.of(new KeywordScore("security", 10)))
                ))
        ));
        seedIndex("3.27", index);

        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("security", "oidc"), null, 10, 0);

        assertThat(result.items()).hasSize(1);
        // Only one keyword matched, no boost — raw score of 10
        assertThat(result.items().get(0).score).isEqualTo(10.0);
    }

    @Test
    void searchSectionsMultiKeywordBoostIsConsistentWithSearchFiles() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc",
                        List.of(new KeywordScore("security", 5), new KeywordScore("oidc", 5)),
                        List.of(
                                new SectionKeywordEntry("Both Keywords", 1, 10,
                                        List.of(new KeywordScore("security", 5), new KeywordScore("oidc", 5)))
                        ))
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> fileResult = searchService.searchFiles(
                "3.27", List.of("security", "oidc"), 10, 0);
        PaginatedResult<SectionSearchResult> sectionResult = searchService.searchSections(
                "3.27", List.of("security", "oidc"), null, 10, 0);

        // Both should apply the same 1.5x boost to the same raw score of 10 → 15
        assertThat(fileResult.items().get(0).score).isEqualTo(15.0);
        assertThat(sectionResult.items().get(0).score).isEqualTo(15.0);
    }

    @Test
    void searchSectionsSearchesAllFilesWhenFilePathsIsNull() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Auth", 1, 10,
                                List.of(new KeywordScore("security", 8)))
                )),
                new FileKeywordEntry("config.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Settings", 1, 10,
                                List.of(new KeywordScore("security", 5)))
                ))
        ));
        seedIndex("3.27", index);

        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("security"), null, 10, 0);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).section).isEqualTo("Auth");
        assertThat(result.items().get(1).section).isEqualTo("Settings");
    }

    @Test
    void searchSectionsSearchesAllFilesWhenFilePathsIsEmpty() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Auth", 1, 10,
                                List.of(new KeywordScore("security", 8)))
                )),
                new FileKeywordEntry("config.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Settings", 1, 10,
                                List.of(new KeywordScore("security", 5)))
                ))
        ));
        seedIndex("3.27", index);

        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("security"), List.of(), 10, 0);

        assertThat(result.items()).hasSize(2);
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
            SearchConfig searchConfig = new TestSearchConfig();
            FuzzyMatcher fuzzyMatcher = new FuzzyMatcher(searchConfig);
            sectionSearchService = new SearchService(
                    keywordIndexStore, codeSampleIndexStore, null, null, realDocStore, docParser, cacheService, searchConfig, fuzzyMatcher);
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
            realDocStore.write("3.27", "security.adoc", docContent);

            SectionContentResult result = sectionSearchService.getSectionContent(
                    "3.27", "security.adoc", "Overview");

            assertThat(result.path).isEqualTo("security.adoc");
            assertThat(result.title).isEqualTo("Overview");
            assertThat(result.content).contains("This is the overview section.");
            assertThat(result.content).contains("It has multiple lines.");
            assertThat(result.startLine).isGreaterThan(0);
            assertThat(result.endLine).isGreaterThanOrEqualTo(result.startLine);
            assertThat(result.matchedTitle).isEqualTo("Overview");
            assertThat(result.matchScore).isEqualTo(1.0);
            assertThat(result.matchType).isEqualTo("exact");
        }

        @Test
        void getSectionContentIsCaseInsensitive() {
            String docContent = """
                    = Main Title
                    Intro.
                    
                    == Security Overview
                    Content here.
                    """;
            realDocStore.write("3.27", "security.adoc", docContent);

            SectionContentResult result = sectionSearchService.getSectionContent(
                    "3.27", "security.adoc", "security overview");

            assertThat(result.title).isEqualTo("Security Overview");
            assertThat(result.content).contains("Content here.");
            assertThat(result.matchedTitle).isEqualTo("Security Overview");
            assertThat(result.matchScore).isEqualTo(1.0);
            assertThat(result.matchType).isEqualTo("exact");
        }

        @Test
        void getSectionContentThrowsWhenDocNotFound() {
            assertThatThrownBy(() ->
                    sectionSearchService.getSectionContent("3.27", "nonexistent.adoc", "Overview"))
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
            realDocStore.write("3.27", "security.adoc", docContent);

            assertThatThrownBy(() ->
                    sectionSearchService.getSectionContent("3.27", "security.adoc", "Completely Unrelated XYZ123"))
                    .isInstanceOf(DocNotFoundException.class)
                    .hasMessageContaining("Section not found");
        }

        @Test
        void getSectionContentFuzzyMatchPartialTitle() {
            String docContent = """
                    = Main Title
                    Intro.
                    
                    == Security Overview
                    Security overview content.
                    
                    == Configuration Guide
                    Config content.
                    """;
            realDocStore.write("3.27", "security.adoc", docContent);

            SectionContentResult result = sectionSearchService.getSectionContent(
                    "3.27", "security.adoc", "Security");

            assertThat(result.title).isEqualTo("Security Overview");
            assertThat(result.content).contains("Security overview content.");
            assertThat(result.matchedTitle).isEqualTo("Security Overview");
            assertThat(result.matchScore).isGreaterThan(0.0).isLessThan(1.0);
            assertThat(result.matchType).isNotEqualTo("exact");
        }

        @Test
        void getSectionContentFuzzyMatchTypo() {
            String docContent = """
                    = Main Title
                    Intro.
                    
                    == Overview
                    Overview content.
                    
                    == Configuration
                    Config content.
                    """;
            realDocStore.write("3.27", "security.adoc", docContent);

            SectionContentResult result = sectionSearchService.getSectionContent(
                    "3.27", "security.adoc", "Overvew");

            assertThat(result.title).isEqualTo("Overview");
            assertThat(result.content).contains("Overview content.");
            assertThat(result.matchedTitle).isEqualTo("Overview");
            assertThat(result.matchScore).isGreaterThan(0.0).isLessThan(1.0);
        }

        @Test
        void getSectionContentFuzzyMatchKeywordOverlap() {
            String docContent = """
                    = Main Title
                    Intro.
                    
                    == Authentication Methods
                    Auth content.
                    
                    == Authorization and Roles
                    Authz content.
                    """;
            realDocStore.write("3.27", "security.adoc", docContent);

            SectionContentResult result = sectionSearchService.getSectionContent(
                    "3.27", "security.adoc", "authentication");

            assertThat(result.title).isEqualTo("Authentication Methods");
            assertThat(result.content).contains("Auth content.");
            assertThat(result.matchedTitle).isEqualTo("Authentication Methods");
        }

        @Test
        void getSectionContentExactMatchTakesPriorityOverFuzzy() {
            String docContent = """
                    = Main Title
                    Intro.
                    
                    == Security
                    Exact match content.
                    
                    == Security Overview
                    Fuzzy match content.
                    """;
            realDocStore.write("3.27", "security.adoc", docContent);

            SectionContentResult result = sectionSearchService.getSectionContent(
                    "3.27", "security.adoc", "Security");

            assertThat(result.title).isEqualTo("Security");
            assertThat(result.content).contains("Exact match content.");
            assertThat(result.matchScore).isEqualTo(1.0);
            assertThat(result.matchType).isEqualTo("exact");
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
            CodeSampleIndexStore lazyCodeSampleIndexStore = new CodeSampleIndexStore(ds);
            SearchConfig searchConfig = new TestSearchConfig();
            FuzzyMatcher fuzzyMatcher = new FuzzyMatcher(searchConfig);
            lazySearchService = new SearchService(lazyKeywordIndexStore, lazyCodeSampleIndexStore,
                    zipDownloadService, keywordIndexer, docStore, docParser, cacheService, searchConfig, fuzzyMatcher);
        }

        @Test
        void searchFilesTriggersDownloadWhenNoIndexAndNoCache() {
            when(docStore.docsExist("3.27")).thenReturn(false);
            when(zipDownloadService.streamAndExtract("3.27"))
                    .thenReturn(List.of("security.adoc", "config.adoc"));
            when(keywordIndexer.build(eq("3.27"), eq(List.of("security.adoc", "config.adoc"))))
                    .thenReturn(new KeywordIndex(List.of()));

            PaginatedResult<FileSearchResult> result = lazySearchService.searchFiles("3.27", List.of("security"), 10, 0);

            verify(zipDownloadService).streamAndExtract("3.27");
            verify(keywordIndexer).build("3.27", List.of("security.adoc", "config.adoc"));
            assertThat(result.items()).isEmpty();
        }

        @Test
        void searchFilesBuildsIndexFromExistingDocsWithoutDownload() {
            when(docStore.docsExist("3.27")).thenReturn(true);
            when(docStore.listDocFiles("3.27")).thenReturn(List.of("security.adoc"));
            when(keywordIndexer.build(eq("3.27"), eq(List.of("security.adoc"))))
                    .thenReturn(new KeywordIndex(List.of()));

            lazySearchService.searchFiles("3.27", List.of("security"), 10, 0);

            verify(zipDownloadService, never()).streamAndExtract("3.27");
            verify(keywordIndexer).build("3.27", List.of("security.adoc"));
        }

        @Test
        void searchFilesDoesNotTriggerDownloadWhenIndexExists() {
            KeywordIndex index = new KeywordIndex(List.of(
                    new FileKeywordEntry("test.adoc",
                            List.of(new KeywordScore("security", 10)), List.of())
            ));
            lazyKeywordIndexStore.write("3.27", index);

            PaginatedResult<FileSearchResult> result = lazySearchService.searchFiles("3.27", List.of("security"), 10, 0);

            verify(zipDownloadService, never()).streamAndExtract("3.27");
            verify(keywordIndexer, never()).build(any(), any());
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).path).isEqualTo("test.adoc");
        }

        @Test
        void searchSectionsTriggersDownloadWhenNoIndexAndNoCache() {
            when(docStore.docsExist("3.27")).thenReturn(false);
            when(zipDownloadService.streamAndExtract("3.27"))
                    .thenReturn(List.of("security.adoc"));
            when(keywordIndexer.build(eq("3.27"), eq(List.of("security.adoc"))))
                    .thenReturn(new KeywordIndex(List.of()));

            PaginatedResult<SectionSearchResult> result = lazySearchService.searchSections(
                    "3.27", List.of("security"), List.of("security.adoc"), 10, 0);

            verify(zipDownloadService).streamAndExtract("3.27");
            verify(keywordIndexer).build("3.27", List.of("security.adoc"));
            assertThat(result.items()).isEmpty();
        }

        @Test
        void searchSectionsDoesNotTriggerDownloadWhenIndexExists() {
            KeywordIndex index = new KeywordIndex(List.of(
                    new FileKeywordEntry("test.adoc", List.of(), List.of(
                            new SectionKeywordEntry("Section 1", 1, 10,
                                    List.of(new KeywordScore("security", 5)))
                    ))
            ));
            lazyKeywordIndexStore.write("3.27", index);

            PaginatedResult<SectionSearchResult> result = lazySearchService.searchSections(
                    "3.27", List.of("security"), List.of("test.adoc"), 10, 0);

            verify(zipDownloadService, never()).streamAndExtract("3.27");
            verify(keywordIndexer, never()).build(any(), any());
            assertThat(result.items()).hasSize(1);
        }
    }

    // --- Code sample search tests ---

    @Nested
    class CodeSampleSearchTests {

        @Test
        void searchCodeSamplesReturnsSortedByDescendingScore() {
            CodeSampleIndex index = new CodeSampleIndex(List.of(
                    new CodeSampleEntry("low.adoc", "Section A", "java", "code1", 1, 5,
                            List.of(new KeywordScore("security", 3))),
                    new CodeSampleEntry("high.adoc", "Section B", "java", "code2", 10, 15,
                            List.of(new KeywordScore("security", 20))),
                    new CodeSampleEntry("mid.adoc", "Section C", "java", "code3", 20, 25,
                            List.of(new KeywordScore("security", 10)))
            ));
            codeSampleIndexStore.write("3.27", index);

            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("security"), null, null, 10, 0);

            assertThat(result.items()).hasSize(3);
            assertThat(result.items().get(0).path).isEqualTo("high.adoc");
            assertThat(result.items().get(0).score).isEqualTo(20.0);
            assertThat(result.items().get(1).path).isEqualTo("mid.adoc");
            assertThat(result.items().get(2).path).isEqualTo("low.adoc");
        }

        @Test
        void searchCodeSamplesAppliesMultiKeywordBoost() {
            CodeSampleIndex index = new CodeSampleIndex(List.of(
                    new CodeSampleEntry("multi.adoc", "Section A", "java", "code1", 1, 5,
                            List.of(new KeywordScore("security", 5), new KeywordScore("oidc", 5))),
                    new CodeSampleEntry("single.adoc", "Section B", "java", "code2", 10, 15,
                            List.of(new KeywordScore("security", 10)))
            ));
            codeSampleIndexStore.write("3.27", index);

            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("security", "oidc"), null, null, 10, 0);

            CodeSampleSearchResult multiResult = result.items().stream()
                    .filter(r -> r.path.equals("multi.adoc")).findFirst().orElseThrow();
            CodeSampleSearchResult singleResult = result.items().stream()
                    .filter(r -> r.path.equals("single.adoc")).findFirst().orElseThrow();
            assertThat(multiResult.score).isGreaterThan(singleResult.score);
        }

        @Test
        void searchCodeSamplesFiltersToFilePath() {
            CodeSampleIndex index = new CodeSampleIndex(List.of(
                    new CodeSampleEntry("included.adoc", "Section A", "java", "code1", 1, 5,
                            List.of(new KeywordScore("security", 5))),
                    new CodeSampleEntry("excluded.adoc", "Section B", "java", "code2", 10, 15,
                            List.of(new KeywordScore("security", 20)))
            ));
            codeSampleIndexStore.write("3.27", index);

            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("security"), "included.adoc", null, 10, 0);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).path).isEqualTo("included.adoc");
        }

        @Test
        void searchCodeSamplesFiltersToSectionTitle() {
            CodeSampleIndex index = new CodeSampleIndex(List.of(
                    new CodeSampleEntry("test.adoc", "Authentication", "java", "code1", 1, 5,
                            List.of(new KeywordScore("security", 5))),
                    new CodeSampleEntry("test.adoc", "Authorization", "java", "code2", 10, 15,
                            List.of(new KeywordScore("security", 8)))
            ));
            codeSampleIndexStore.write("3.27", index);

            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("security"), null, "Authentication", 10, 0);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).sectionTitle).isEqualTo("Authentication");
        }

        @Test
        void searchCodeSamplesFiltersBothFilePathAndSectionTitle() {
            CodeSampleIndex index = new CodeSampleIndex(List.of(
                    new CodeSampleEntry("a.adoc", "Overview", "java", "code1", 1, 5,
                            List.of(new KeywordScore("security", 5))),
                    new CodeSampleEntry("a.adoc", "Config", "java", "code2", 10, 15,
                            List.of(new KeywordScore("security", 8))),
                    new CodeSampleEntry("b.adoc", "Overview", "java", "code3", 1, 5,
                            List.of(new KeywordScore("security", 12)))
            ));
            codeSampleIndexStore.write("3.27", index);

            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("security"), "a.adoc", "Overview", 10, 0);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).path).isEqualTo("a.adoc");
            assertThat(result.items().get(0).sectionTitle).isEqualTo("Overview");
        }

        @Test
        void searchCodeSamplesReturnsEmptyForUnknownKeyword() {
            CodeSampleIndex index = new CodeSampleIndex(List.of(
                    new CodeSampleEntry("test.adoc", "Section A", "java", "code1", 1, 5,
                            List.of(new KeywordScore("security", 10)))
            ));
            codeSampleIndexStore.write("3.27", index);

            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("nonexistent"), null, null, 10, 0);

            assertThat(result.items()).isEmpty();
        }

        @Test
        void searchCodeSamplesReturnsEmptyWhenNoIndex() {
            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("security"), null, null, 10, 0);

            assertThat(result.items()).isEmpty();
        }

        @Test
        void searchCodeSamplesPaginationLimitsResults() {
            List<CodeSampleEntry> samples = new java.util.ArrayList<>();
            for (int i = 0; i < 15; i++) {
                samples.add(new CodeSampleEntry("file" + i + ".adoc", "Section", "java",
                        "code" + i, i * 5 + 1, (i + 1) * 5,
                        List.of(new KeywordScore("test", 15 - i))));
            }
            codeSampleIndexStore.write("3.27", new CodeSampleIndex(samples));

            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("test"), null, null, 5, 0);

            assertThat(result.items()).hasSize(5);
            assertThat(result.total()).isEqualTo(15);
            assertThat(result.items().get(0).path).isEqualTo("file0.adoc");
        }

        @Test
        void searchCodeSamplesPaginationWithOffset() {
            List<CodeSampleEntry> samples = new java.util.ArrayList<>();
            for (int i = 0; i < 15; i++) {
                samples.add(new CodeSampleEntry("file" + i + ".adoc", "Section", "java",
                        "code" + i, i * 5 + 1, (i + 1) * 5,
                        List.of(new KeywordScore("test", 15 - i))));
            }
            codeSampleIndexStore.write("3.27", new CodeSampleIndex(samples));

            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("test"), null, null, 5, 5);

            assertThat(result.items()).hasSize(5);
            assertThat(result.total()).isEqualTo(15);
            assertThat(result.items().get(0).path).isEqualTo("file5.adoc");
        }

        @Test
        void searchCodeSamplesReturnsAllFields() {
            CodeSampleIndex index = new CodeSampleIndex(List.of(
                    new CodeSampleEntry("security.adoc", "Authentication", "java",
                            "import io.quarkus.Security;", 5, 10,
                            List.of(new KeywordScore("security", 15)))
            ));
            codeSampleIndexStore.write("3.27", index);

            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("security"), null, null, 10, 0);

            assertThat(result.items()).hasSize(1);
            CodeSampleSearchResult r = result.items().get(0);
            assertThat(r.path).isEqualTo("security.adoc");
            assertThat(r.sectionTitle).isEqualTo("Authentication");
            assertThat(r.language).isEqualTo("java");
            assertThat(r.content).isEqualTo("import io.quarkus.Security;");
            assertThat(r.startLine).isEqualTo(5);
            assertThat(r.endLine).isEqualTo(10);
            assertThat(r.score).isEqualTo(15.0);
        }

        @Test
        void searchCodeSamplesSectionTitleFilterIsCaseInsensitive() {
            CodeSampleIndex index = new CodeSampleIndex(List.of(
                    new CodeSampleEntry("test.adoc", "Authentication", "java", "code1", 1, 5,
                            List.of(new KeywordScore("security", 5)))
            ));
            codeSampleIndexStore.write("3.27", index);

            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("security"), null, "authentication", 10, 0);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).sectionTitle).isEqualTo("Authentication");
        }
    }

    @Nested
    class ContentSearchTests {

        private SearchService contentSearchService;
        private DocStore realDocStore;

        @BeforeEach
        void setUpContentSearch() {
            CacheService contentCacheService = new CacheService(tempDir.toString());
            realDocStore = new DocStore(contentCacheService);
            SearchConfig searchConfig = new TestSearchConfig();
            FuzzyMatcher fuzzyMatcher = new FuzzyMatcher(searchConfig);
            contentSearchService = new SearchService(
                    keywordIndexStore, codeSampleIndexStore, null, null, realDocStore, docParser, contentCacheService, searchConfig, fuzzyMatcher);
        }

        @Test
        void searchContentReturnsMatchingFiles() {
            realDocStore.write("3.27", "security.adoc",
                    "= Security Guide\nThis document covers security and authentication.");
            realDocStore.write("3.27", "config.adoc",
                    "= Config Guide\nThis document covers configuration.");

            PaginatedResult<ContentSearchResult> result = contentSearchService.searchContent(
                    "3.27", List.of("security"), 10, 0);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).path).isEqualTo("security.adoc");
            assertThat(result.items().get(0).score).isGreaterThan(0);
        }

        @Test
        void searchContentReturnsEmptyWhenNoMatch() {
            realDocStore.write("3.27", "config.adoc",
                    "= Config Guide\nThis document covers configuration.");

            PaginatedResult<ContentSearchResult> result = contentSearchService.searchContent(
                    "3.27", List.of("security"), 10, 0);

            assertThat(result.items()).isEmpty();
            assertThat(result.total()).isEqualTo(0);
        }

        @Test
        void searchContentReturnsEmptyWhenNoFiles() {
            PaginatedResult<ContentSearchResult> result = contentSearchService.searchContent(
                    "3.27", List.of("security"), 10, 0);

            assertThat(result.items()).isEmpty();
            assertThat(result.total()).isEqualTo(0);
        }

        @Test
        void searchContentIsCaseInsensitive() {
            realDocStore.write("3.27", "security.adoc",
                    "= Guide\nThis document covers SECURITY and Authentication.");

            PaginatedResult<ContentSearchResult> result = contentSearchService.searchContent(
                    "3.27", List.of("security"), 10, 0);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).path).isEqualTo("security.adoc");
        }

        @Test
        void searchContentGeneratesSnippet() {
            realDocStore.write("3.27", "security.adoc",
                    "= Guide\nThis document covers security and authentication in Quarkus.");

            PaginatedResult<ContentSearchResult> result = contentSearchService.searchContent(
                    "3.27", List.of("security"), 10, 0);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).snippet).contains("security");
            assertThat(result.items().get(0).snippet).isNotEmpty();
        }

        @Test
        void searchContentReturnsMatchOffsetAndLine() {
            String content = "= Guide\nLine two.\nLine three has security info.";
            realDocStore.write("3.27", "security.adoc", content);

            PaginatedResult<ContentSearchResult> result = contentSearchService.searchContent(
                    "3.27", List.of("security"), 10, 0);

            assertThat(result.items()).hasSize(1);
            ContentSearchResult r = result.items().get(0);
            assertThat(r.matchOffset).isGreaterThan(0);
            assertThat(r.matchLine).isEqualTo(3); // "security" is on line 3
        }

        @Test
        void searchContentSortsByScoreDescending() {
            realDocStore.write("3.27", "many.adoc",
                    "security security security security security");
            realDocStore.write("3.27", "few.adoc",
                    "security once only");

            PaginatedResult<ContentSearchResult> result = contentSearchService.searchContent(
                    "3.27", List.of("security"), 10, 0);

            assertThat(result.items()).hasSize(2);
            assertThat(result.items().get(0).path).isEqualTo("many.adoc");
            assertThat(result.items().get(0).score).isGreaterThan(result.items().get(1).score);
        }

        @Test
        void searchContentAppliesMultiKeywordBoost() {
            realDocStore.write("3.27", "both.adoc",
                    "This has security and oidc content.");
            realDocStore.write("3.27", "one.adoc",
                    "This has only security content here.");

            PaginatedResult<ContentSearchResult> result = contentSearchService.searchContent(
                    "3.27", List.of("security", "oidc"), 10, 0);

            assertThat(result.items()).hasSize(2);
            // "both" should have boosted score
            ContentSearchResult bothResult = result.items().stream()
                    .filter(r -> r.path.equals("both.adoc")).findFirst().orElseThrow();
            ContentSearchResult oneResult = result.items().stream()
                    .filter(r -> r.path.equals("one.adoc")).findFirst().orElseThrow();
            assertThat(bothResult.score).isGreaterThan(oneResult.score);
        }

        @Test
        void searchContentDoesNotApplyBoostWhenOnlyOneKeywordMatches() {
            realDocStore.write("3.27", "one-match.adoc",
                    "This has security but not the other keyword.");

            PaginatedResult<ContentSearchResult> result = contentSearchService.searchContent(
                    "3.27", List.of("security", "oidc"), 10, 0);

            assertThat(result.items()).hasSize(1);
            // Only "security" matched, raw count is 1, no boost
            assertThat(result.items().get(0).score).isEqualTo(1.0);
        }

        @Test
        void searchContentDoesNotApplyBoostForSingleQueryKeyword() {
            realDocStore.write("3.27", "multi-occur.adoc",
                    "security security security");

            PaginatedResult<ContentSearchResult> result = contentSearchService.searchContent(
                    "3.27", List.of("security"), 10, 0);

            assertThat(result.items()).hasSize(1);
            // 3 occurrences, single keyword → no boost → score = 3.0
            assertThat(result.items().get(0).score).isEqualTo(3.0);
        }

        @Test
        void searchContentPaginationLimitsResults() {
            for (int i = 0; i < 5; i++) {
                realDocStore.write("3.27", "doc" + i + ".adoc",
                        "security content in doc " + i);
            }

            PaginatedResult<ContentSearchResult> result = contentSearchService.searchContent(
                    "3.27", List.of("security"), 2, 0);

            assertThat(result.items()).hasSize(2);
            assertThat(result.total()).isEqualTo(5);
        }

        @Test
        void searchContentPaginationWithOffset() {
            for (int i = 0; i < 5; i++) {
                realDocStore.write("3.27", "doc" + i + ".adoc",
                        "security content in doc " + i);
            }

            PaginatedResult<ContentSearchResult> result = contentSearchService.searchContent(
                    "3.27", List.of("security"), 2, 3);

            assertThat(result.items()).hasSize(2);
            assertThat(result.total()).isEqualTo(5);
        }

        @Test
        void searchContentPaginationOffsetBeyondResults() {
            realDocStore.write("3.27", "doc.adoc", "security content");

            PaginatedResult<ContentSearchResult> result = contentSearchService.searchContent(
                    "3.27", List.of("security"), 10, 100);

            assertThat(result.items()).isEmpty();
            assertThat(result.total()).isEqualTo(1);
        }
    }
}
