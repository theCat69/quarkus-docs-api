package com.fvd.search.services;

import com.fvd.asciidocs.parser.AsciidocParser;
import com.fvd.cache.services.CacheService;
import com.fvd.common.TestSqliteHelper;
import com.fvd.common.matchers.FuzzyMatcher;
import com.fvd.docs.exceptions.DocNotFoundException;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.CodeSampleEntry;
import com.fvd.indexs.indexers.CodeSampleIndex;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.indexs.indexers.SectionKeywordEntry;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.search.SearchConfig;
import com.fvd.search.TestSearchConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        SQLiteDataSource ds = TestSqliteHelper.createInitializedDataSource(tempDir);
        keywordIndexStore = new KeywordIndexStore(ds);
        codeSampleIndexStore = new CodeSampleIndexStore(ds);
        docParser = new AsciidocParser(new TestSearchConfig());
        cacheService = new CacheService(tempDir.toString());
        SearchConfig searchConfig = new TestSearchConfig();
        FuzzyMatcher fuzzyMatcher = new FuzzyMatcher(searchConfig);
        SearchScorer searchScorer = new SqliteSearchScorer(searchConfig);
        searchService = new SearchService(keywordIndexStore, codeSampleIndexStore, null, docParser, cacheService, searchConfig, fuzzyMatcher, searchScorer);
    }

    private void seedIndex(String version, KeywordIndex index) {
        keywordIndexStore.write(version, index);
    }

    private SearchService createSearchServiceWithDocStore(DocStore docStore) {
        CacheService cs = new CacheService(tempDir.toString());
        SearchConfig searchConfig = new TestSearchConfig();
        FuzzyMatcher fuzzyMatcher = new FuzzyMatcher(searchConfig);
        SearchScorer searchScorer = new SqliteSearchScorer(searchConfig);
        return new SearchService(
                keywordIndexStore, codeSampleIndexStore, docStore, docParser, cs, searchConfig, fuzzyMatcher, searchScorer);
    }

    // --- File search tests ---

    @Test
    void searchFilesReturnsSortedByDescendingScore() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("low.adoc",
                        List.of(new KeywordScore("secur", 5)), List.of()),
                new FileKeywordEntry("high.adoc",
                        List.of(new KeywordScore("secur", 20)), List.of()),
                new FileKeywordEntry("mid.adoc",
                        List.of(new KeywordScore("secur", 12)), List.of())
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("security"), null, 10, 0);

        assertThat(result.items()).hasSize(3);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.items().get(0).path).isEqualTo("high.adoc");
        assertThat(result.items().get(0).score).isEqualTo(20.0);
        assertThat(extractKeywords(result.items().get(0).matchedKeywords)).containsExactly("secur");
        assertThat(result.items().get(1).path).isEqualTo("mid.adoc");
        assertThat(result.items().get(2).path).isEqualTo("low.adoc");
    }

    @Test
    void searchFilesAggregatesScoresAcrossKeywords() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("both.adoc",
                        List.of(new KeywordScore("secur", 10), new KeywordScore("oidc", 8)), List.of()),
                new FileKeywordEntry("one.adoc",
                        List.of(new KeywordScore("secur", 15)), List.of())
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("security", "oidc"), null, 10, 0);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).path).isEqualTo("both.adoc");
        assertThat(result.items().get(0).score).isGreaterThan(result.items().get(1).score);
        assertThat(extractKeywords(result.items().get(0).matchedKeywords)).containsExactlyInAnyOrder("secur", "oidc");
        assertThat(extractKeywords(result.items().get(1).matchedKeywords)).containsExactly("secur");
    }

    @Test
    void searchFilesReturnsMatchedKeywordsWithSourceAndWeight() {
        // Create index with keywords that have source information
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc",
                        List.of(
                                new KeywordScore("secur", 10, "filename", 1),
                                new KeywordScore("oidc", 5, "section", 2)
                        ), List.of())
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> result = searchService.searchFiles(
                "3.27", List.of("security", "oidc"), null, 10, 0);

        assertThat(result.items()).hasSize(1);
        List<MatchedKeyword> matched = result.items().get(0).matchedKeywords;
        assertThat(matched).hasSize(2);
        
        // Verify source and weight are populated
        MatchedKeyword securMatch = matched.stream()
                .filter(m -> m.keyword().equals("secur")).findFirst().orElseThrow();
        assertThat(securMatch.source()).isEqualTo("filename");
        assertThat(securMatch.weight()).isEqualTo(10.0);
        
        MatchedKeyword oidcMatch = matched.stream()
                .filter(m -> m.keyword().equals("oidc")).findFirst().orElseThrow();
        assertThat(oidcMatch.source()).isEqualTo("section");
        assertThat(oidcMatch.weight()).isEqualTo(5.0);
    }

    @Test
    void searchFilesMultiKeywordBoostIncreasesScore() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("multi.adoc",
                        List.of(new KeywordScore("secur", 5), new KeywordScore("oidc", 5)), List.of()),
                new FileKeywordEntry("single.adoc",
                        List.of(new KeywordScore("secur", 10)), List.of())
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("security", "oidc"), null, 10, 0);

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

        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("test"), null, 5, 0);

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

        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("test"), null, 5, 5);

        assertThat(result.items()).hasSize(5);
        assertThat(result.total()).isEqualTo(15);
        assertThat(result.items().get(0).path).isEqualTo("file5.adoc");
        assertThat(result.items().get(4).path).isEqualTo("file9.adoc");
    }

    @Test
    void searchFilesPaginationOffsetBeyondResults() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc",
                        List.of(new KeywordScore("secur", 10)), List.of())
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("security"), null, 10, 100);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void searchFilesReturnsEmptyForUnknownKeyword() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc",
                        List.of(new KeywordScore("secur", 10)), List.of())
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("nonexistent"), null, 10, 0);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualTo(0);
    }

    @Test
    void searchFilesReturnsEmptyWhenNoIndexAndNoDeps() {
        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("security"), null, 10, 0);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualTo(0);
    }

    // --- Prefix matching tests ---

    @Test
    void searchFilesPrefixMatchReturnsResults() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc",
                        List.of(new KeywordScore("secur", 10)), List.of())
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("secur"), null, 10, 0);

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
        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("secur"), null, 10, 0);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void searchFilesPrefixMatchAppliesDiscount() {
        // Indexed word "configur" (stemmed form), query "config" is a prefix
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc",
                        List.of(new KeywordScore("configur", 10)), List.of())
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> exactResult = searchService.searchFiles("3.27", List.of("configuration"), null, 10, 0);
        PaginatedResult<FileSearchResult> prefixResult = searchService.searchFiles("3.27", List.of("config"), null, 10, 0);

        // "configuration" stems to "configur" → exact match: score = 10.0
        assertThat(exactResult.items().get(0).score).isEqualTo(10.0);
        // "config" stems to "config" → prefix match of "configur": score = 10.0 * 0.8 = 8.0
        assertThat(prefixResult.items().get(0).score).isEqualTo(8.0);
    }

    @Test
    void searchFilesExactMatchTakesPrecedenceOverPrefix() {
        // "configuration" stems to "configur" → exact match with indexed "configur"
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc",
                        List.of(new KeywordScore("configur", 10)), List.of())
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("configuration"), null, 10, 0);

        assertThat(result.items().get(0).score).isEqualTo(10.0); // full score, not discounted
    }

    @Test
    void searchFilesQueryKeywordLongerThanIndexedDoesNotMatch() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc",
                        List.of(new KeywordScore("sec", 10)), List.of())
        ));
        seedIndex("3.27", index);

        // "security" stems to "secur", which is longer than "sec"
        // "sec".startsWith("secur") is false → no match
        PaginatedResult<FileSearchResult> result = searchService.searchFiles("3.27", List.of("security"), null, 10, 0);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void searchSectionsPrefixMatchReturnsResults() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Auth Section", 1, 10,
                                List.of(new KeywordScore("authentic", 8)))
                ))
        ));
        seedIndex("3.27", index);

        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("auth"), null, null, null, 10, 0);

        assertThat(result.items()).hasSize(1);
        // Prefix discount: 8 * 0.8 = 6.4
        assertThat(result.items().get(0).score).isEqualTo(6.4);
    }

    @Test
    void searchCodeSamplesPrefixMatchReturnsResults() {
        CodeSampleIndex index = new CodeSampleIndex(List.of(
                new CodeSampleEntry("test.adoc", "Section A", "java", "code1", 1, 5,
                        List.of(new KeywordScore("configur", 10)))
        ));
        codeSampleIndexStore.write("3.27", index);

        PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                "3.27", List.of("config"), null, null, null, 10, 0);

        assertThat(result.items()).hasSize(1);
        // "config" stems to "config", prefix of "configur": 10 * 0.8 = 8.0
        assertThat(result.items().get(0).score).isEqualTo(8.0);
    }

    // --- Section search tests ---

    @Test
    void searchSectionsReturnsMatchingSections() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Overview", 1, 10,
                                List.of(new KeywordScore("secur", 8))),
                        new SectionKeywordEntry("OIDC Config", 11, 30,
                                List.of(new KeywordScore("oidc", 12), new KeywordScore("secur", 3)))
                ))
        ));
        seedIndex("3.27", index);

        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("security"), List.of("security.adoc"), null, null, 10, 0);

        assertThat(result.items()).isNotEmpty();
        assertThat(result.items().get(0).section).isEqualTo("Overview"); // score 8 > 3
        assertThat(result.items().get(0).start).isEqualTo(1);
        assertThat(result.items().get(0).end).isEqualTo(10);
        assertThat(extractKeywords(result.items().get(0).matchedKeywords)).containsExactly("secur");
    }

    @Test
    void searchSectionsFiltersToProvidedFilePaths() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("included.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Included", 1, 10,
                                List.of(new KeywordScore("secur", 5)))
                )),
                new FileKeywordEntry("excluded.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Excluded", 1, 10,
                                List.of(new KeywordScore("secur", 20)))
                ))
        ));
        seedIndex("3.27", index);

        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("security"), List.of("included.adoc"), null, null, 10, 0);

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
                "3.27", List.of("test"), List.of("big.adoc"), null, null, 3, 0);

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
                "3.27", List.of("test"), List.of("big.adoc"), null, null, 3, 3);

        assertThat(result.items()).hasSize(3);
        assertThat(result.total()).isEqualTo(8);
        assertThat(result.items().get(0).section).isEqualTo("Section 3");
    }

    @Test
    void searchSectionsReturnsEmptyForUnmatchedKeyword() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Section", 1, 10,
                                List.of(new KeywordScore("secur", 5)))
                ))
        ));
        seedIndex("3.27", index);

        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("nonexistent"), List.of("test.adoc"), null, null, 10, 0);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void searchSectionsReturnsEmptyWhenNoIndexAndNoDeps() {
        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("security"), List.of("test.adoc"), null, null, 10, 0);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void searchSectionsAppliesMultiKeywordBoostWhenBothMatch() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Both Keywords", 1, 10,
                                List.of(new KeywordScore("secur", 5), new KeywordScore("oidc", 5)))
                ))
        ));
        seedIndex("3.27", index);

        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("security", "oidc"), null, null, null, 10, 0);

        assertThat(result.items()).hasSize(1);
        // Raw sum is 10, with 1.5x boost should be 15
        assertThat(result.items().get(0).score).isEqualTo(15.0);
        assertThat(extractKeywords(result.items().get(0).matchedKeywords)).containsExactlyInAnyOrder("secur", "oidc");
    }

    @Test
    void searchSectionsDoesNotApplyBoostWhenOnlyOneKeywordMatches() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc", List.of(), List.of(
                        new SectionKeywordEntry("One Match", 1, 10,
                                List.of(new KeywordScore("secur", 10)))
                ))
        ));
        seedIndex("3.27", index);

        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("security", "oidc"), null, null, null, 10, 0);

        assertThat(result.items()).hasSize(1);
        // Only one keyword matched, no boost — raw score of 10
        assertThat(result.items().get(0).score).isEqualTo(10.0);
    }

    @Test
    void searchSectionsMultiKeywordBoostIsConsistentWithSearchFiles() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc",
                        List.of(new KeywordScore("secur", 5), new KeywordScore("oidc", 5)),
                        List.of(
                                new SectionKeywordEntry("Both Keywords", 1, 10,
                                        List.of(new KeywordScore("secur", 5), new KeywordScore("oidc", 5)))
                        ))
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> fileResult = searchService.searchFiles(
                "3.27", List.of("security", "oidc"), null, 10, 0);
        PaginatedResult<SectionSearchResult> sectionResult = searchService.searchSections(
                "3.27", List.of("security", "oidc"), null, null, null, 10, 0);

        // Both should apply the same 1.5x boost to the same raw score of 10 → 15
        assertThat(fileResult.items().get(0).score).isEqualTo(15.0);
        assertThat(sectionResult.items().get(0).score).isEqualTo(15.0);
    }

    static Stream<Arguments> nullAndEmptyFilePaths() {
        return Stream.of(
                Arguments.of((List<String>) null),
                Arguments.of(List.of())
        );
    }

    @ParameterizedTest(name = "filePaths={0} searches all files")
    @MethodSource("nullAndEmptyFilePaths")
    void searchSectionsSearchesAllFilesWhenFilePathsIsNullOrEmpty(List<String> filePaths) {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Auth", 1, 10,
                                List.of(new KeywordScore("secur", 8)))
                )),
                new FileKeywordEntry("config.adoc", List.of(), List.of(
                        new SectionKeywordEntry("Settings", 1, 10,
                                List.of(new KeywordScore("secur", 5)))
                ))
        ));
        seedIndex("3.27", index);

        PaginatedResult<SectionSearchResult> result = searchService.searchSections(
                "3.27", List.of("security"), filePaths, null, null, 10, 0);

        assertThat(result.items()).hasSize(2);
    }

    // --- Section content tests ---

    @Nested
    class SectionContentTests {

        private SearchService sectionSearchService;
        private DocStore realDocStore;

        @BeforeEach
        void setUpSectionContent() {
            CacheService cs = new CacheService(tempDir.toString());
            realDocStore = new DocStore(cs);
            sectionSearchService = createSearchServiceWithDocStore(realDocStore);
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

    // --- Section search snippet and sectionTitle filter tests ---

    @Nested
    class SectionSearchSnippetAndFilterTests {

        private SearchService snippetSearchService;
        private DocStore realDocStore;

        @BeforeEach
        void setUpSnippetTests() {
            CacheService cs = new CacheService(tempDir.toString());
            realDocStore = new DocStore(cs);
            snippetSearchService = createSearchServiceWithDocStore(realDocStore);
        }

        @Test
        void searchSectionsGeneratesSnippetAroundKeywordMatch() {
            String docContent = """
                    = Security Guide
                    Introduction text.
                    
                    == Overview
                    This section covers the security features of Quarkus including authentication and authorization mechanisms for enterprise applications.
                    """;
            realDocStore.write("3.27", "security.adoc", docContent);
            KeywordIndex index = new KeywordIndex(List.of(
                    new FileKeywordEntry("security.adoc", List.of(), List.of(
                            new SectionKeywordEntry("Overview", 4, 5,
                                    List.of(new KeywordScore("secur", 10)))
                    ))
            ));
            keywordIndexStore.write("3.27", index);
            snippetSearchService.invalidateCache("3.27");

            PaginatedResult<SectionSearchResult> result = snippetSearchService.searchSections(
                    "3.27", List.of("security"), null, null, null, 10, 0);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).snippet).isNotNull();
            assertThat(result.items().get(0).snippet).isNotEmpty();
        }

        @Test
        void searchSectionsSnippetFallbackUsesFirstCharsWhenKeywordNotFound() {
            String docContent = """
                    = Guide
                    Intro.
                    
                    == Overview
                    This is a section with some general content that does not contain the searched keyword anywhere in its text body.
                    """;
            realDocStore.write("3.27", "guide.adoc", docContent);
            KeywordIndex index = new KeywordIndex(List.of(
                    new FileKeywordEntry("guide.adoc", List.of(), List.of(
                            new SectionKeywordEntry("Overview", 4, 5,
                                    List.of(new KeywordScore("oidc", 5)))
                    ))
            ));
            keywordIndexStore.write("3.27", index);
            snippetSearchService.invalidateCache("3.27");

            PaginatedResult<SectionSearchResult> result = snippetSearchService.searchSections(
                    "3.27", List.of("oidc"), null, null, null, 10, 0);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).snippet).isNotNull();
            // Snippet should be the first ~100 chars since "oidc" is not in the section content
            assertThat(result.items().get(0).snippet).doesNotContain("oidc");
            assertThat(result.items().get(0).snippet.length()).isLessThanOrEqualTo(103); // 100 + "..."
        }

        @Test
        void searchSectionsSectionTitleFilterReturnsFuzzyMatchedSections() {
            KeywordIndex index = new KeywordIndex(List.of(
                    new FileKeywordEntry("security.adoc", List.of(), List.of(
                            new SectionKeywordEntry("Authentication Methods", 1, 10,
                                    List.of(new KeywordScore("secur", 8))),
                            new SectionKeywordEntry("Authorization and Roles", 11, 20,
                                    List.of(new KeywordScore("secur", 5)))
                    ))
            ));
            keywordIndexStore.write("3.27", index);
            snippetSearchService.invalidateCache("3.27");

            PaginatedResult<SectionSearchResult> result = snippetSearchService.searchSections(
                    "3.27", List.of("security"), null, "authentication", null, 10, 0);

            assertThat(result.items()).isNotEmpty();
            assertThat(result.items()).allMatch(r -> r.section.equals("Authentication Methods"));
            assertThat(result.items().get(0).matchedSectionTitle).isEqualTo("Authentication Methods");
            assertThat(result.items().get(0).sectionMatchScore).isGreaterThan(0.0);
        }

        @Test
        void searchSectionsSectionTitleNoMatchReturnsEmpty() {
            KeywordIndex index = new KeywordIndex(List.of(
                    new FileKeywordEntry("security.adoc", List.of(), List.of(
                            new SectionKeywordEntry("Authentication Methods", 1, 10,
                                    List.of(new KeywordScore("secur", 8)))
                    ))
            ));
            keywordIndexStore.write("3.27", index);
            snippetSearchService.invalidateCache("3.27");

            PaginatedResult<SectionSearchResult> result = snippetSearchService.searchSections(
                    "3.27", List.of("security"), null, "ZzzzCompletelyDifferent", null, 10, 0);

            assertThat(result.items()).isEmpty();
            assertThat(result.total()).isEqualTo(0);
        }
    }

    // --- Code sample search tests ---

    @Nested
    class CodeSampleSearchTests {

        @Test
        void searchCodeSamplesReturnsSortedByDescendingScore() {
            CodeSampleIndex index = new CodeSampleIndex(List.of(
                    new CodeSampleEntry("low.adoc", "Section A", "java", "code1", 1, 5,
                            List.of(new KeywordScore("secur", 3))),
                    new CodeSampleEntry("high.adoc", "Section B", "java", "code2", 10, 15,
                            List.of(new KeywordScore("secur", 20))),
                    new CodeSampleEntry("mid.adoc", "Section C", "java", "code3", 20, 25,
                            List.of(new KeywordScore("secur", 10)))
            ));
            codeSampleIndexStore.write("3.27", index);

            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("security"), null, null, null, 10, 0);

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
                            List.of(new KeywordScore("secur", 5), new KeywordScore("oidc", 5))),
                    new CodeSampleEntry("single.adoc", "Section B", "java", "code2", 10, 15,
                            List.of(new KeywordScore("secur", 10)))
            ));
            codeSampleIndexStore.write("3.27", index);

            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("security", "oidc"), null, null, null, 10, 0);

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
                            List.of(new KeywordScore("secur", 5))),
                    new CodeSampleEntry("excluded.adoc", "Section B", "java", "code2", 10, 15,
                            List.of(new KeywordScore("secur", 20)))
            ));
            codeSampleIndexStore.write("3.27", index);

            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("security"), "included.adoc", null, null, 10, 0);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).path).isEqualTo("included.adoc");
        }

        @Test
        void searchCodeSamplesFiltersToSectionTitle() {
            CodeSampleIndex index = new CodeSampleIndex(List.of(
                    new CodeSampleEntry("test.adoc", "Authentication", "java", "code1", 1, 5,
                            List.of(new KeywordScore("secur", 5))),
                    new CodeSampleEntry("test.adoc", "Authorization", "java", "code2", 10, 15,
                            List.of(new KeywordScore("secur", 8)))
            ));
            codeSampleIndexStore.write("3.27", index);

            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("security"), null, "authentication", null, 10, 0);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).sectionTitle).isEqualTo("Authentication");
        }

        @Test
        void searchCodeSamplesExactSectionTitleStillMatches() {
            CodeSampleIndex index = new CodeSampleIndex(List.of(
                    new CodeSampleEntry("test.adoc", "Authentication", "java", "code1", 1, 5,
                            List.of(new KeywordScore("secur", 5))),
                    new CodeSampleEntry("test.adoc", "Authorization", "java", "code2", 10, 15,
                            List.of(new KeywordScore("secur", 8)))
            ));
            codeSampleIndexStore.write("3.27", index);

            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("security"), null, "Authentication", null, 10, 0);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).sectionTitle).isEqualTo("Authentication");
            assertThat(result.items().get(0).matchedSectionTitle).isEqualTo("Authentication");
            assertThat(result.items().get(0).sectionMatchScore).isEqualTo(1.0);
        }

        @Test
        void searchCodeSamplesFuzzySectionTitleMatchesPartial() {
            CodeSampleIndex index = new CodeSampleIndex(List.of(
                    new CodeSampleEntry("test.adoc", "Authentication", "java", "code1", 1, 5,
                            List.of(new KeywordScore("secur", 5))),
                    new CodeSampleEntry("test.adoc", "Authorization", "java", "code2", 10, 15,
                            List.of(new KeywordScore("secur", 8)))
            ));
            codeSampleIndexStore.write("3.27", index);

            // "Authenticat" is a close partial match for "Authentication"
            // FuzzyMatcher should pick it above threshold
            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("security"), null, "Authenticat", null, 10, 0);

            assertThat(result.items()).isNotEmpty();
            assertThat(result.items().get(0).matchedSectionTitle).isEqualTo("Authentication");
            assertThat(result.items().get(0).sectionMatchScore).isGreaterThan(0.0);
        }

        @Test
        void searchCodeSamplesSectionTitleBelowThresholdReturnsEmpty() {
            CodeSampleIndex index = new CodeSampleIndex(List.of(
                    new CodeSampleEntry("test.adoc", "Authentication", "java", "code1", 1, 5,
                            List.of(new KeywordScore("secur", 5)))
            ));
            codeSampleIndexStore.write("3.27", index);

            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("security"), null, "ZzzzCompletelyDifferent", null, 10, 0);

            assertThat(result.items()).isEmpty();
        }

        @Test
        void searchCodeSamplesMatchedSectionTitlePopulatedWhenFiltered() {
            CodeSampleIndex index = new CodeSampleIndex(List.of(
                    new CodeSampleEntry("test.adoc", "Authentication", "java", "code1", 1, 5,
                            List.of(new KeywordScore("secur", 5)))
            ));
            codeSampleIndexStore.write("3.27", index);

            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("security"), null, "authentication", null, 10, 0);

            assertThat(result.items()).hasSize(1);
            CodeSampleSearchResult r = result.items().get(0);
            assertThat(r.matchedSectionTitle).isEqualTo("Authentication");
            assertThat(r.sectionMatchScore).isEqualTo(1.0);
        }

        @Test
        void searchCodeSamplesMatchedSectionTitleNullWhenNoFilter() {
            CodeSampleIndex index = new CodeSampleIndex(List.of(
                    new CodeSampleEntry("test.adoc", "Authentication", "java", "code1", 1, 5,
                            List.of(new KeywordScore("secur", 5)))
            ));
            codeSampleIndexStore.write("3.27", index);

            PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                    "3.27", List.of("security"), null, null, null, 10, 0);

            assertThat(result.items()).hasSize(1);
            CodeSampleSearchResult r = result.items().get(0);
            assertThat(r.matchedSectionTitle).isNull();
            assertThat(r.sectionMatchScore).isEqualTo(0.0);
        }
    }

    // --- Stemming equivalence tests ---

    @Test
    void searchFilesMorphologicalVariantsReturnSameResults() {
        // "configuration" (stem: "configur"), "configurable" (stem: "configur"),
        // "configured" (stem: "configur") — all stem to the same form
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("config.adoc",
                        List.of(new KeywordScore("configur", 10)), List.of())
        ));
        seedIndex("3.27", index);

        PaginatedResult<FileSearchResult> resultConfiguration = searchService.searchFiles("3.27", List.of("configuration"), null, 10, 0);
        PaginatedResult<FileSearchResult> resultConfigurable = searchService.searchFiles("3.27", List.of("configurable"), null, 10, 0);
        PaginatedResult<FileSearchResult> resultConfigured = searchService.searchFiles("3.27", List.of("configured"), null, 10, 0);

        assertThat(resultConfiguration.items()).hasSize(1);
        assertThat(resultConfigurable.items()).hasSize(1);
        assertThat(resultConfigured.items()).hasSize(1);
        assertThat(resultConfiguration.items().get(0).score)
                .isEqualTo(resultConfigurable.items().get(0).score)
                .isEqualTo(resultConfigured.items().get(0).score);
    }

    @Test
    void searchSectionsMorphologicalVariantsReturnSameResults() {
        // "security" (stem: "secur"), "secured" (stem: "secur") — both stem to "secur"
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc",
                        List.of(new KeywordScore("secur", 10)),
                        List.of(new SectionKeywordEntry("Overview", 1, 10,
                                List.of(new KeywordScore("secur", 8)))))
        ));
        seedIndex("3.27", index);

        PaginatedResult<SectionSearchResult> resultSecurity = searchService.searchSections(
                "3.27", List.of("security"), null, null, null, 10, 0);
        PaginatedResult<SectionSearchResult> resultSecured = searchService.searchSections(
                "3.27", List.of("secured"), null, null, null, 10, 0);

        assertThat(resultSecurity.items()).hasSize(1);
        assertThat(resultSecured.items()).hasSize(1);
        assertThat(resultSecurity.items().get(0).score)
                .isEqualTo(resultSecured.items().get(0).score);
    }

    // --- Extension filtering tests ---

    /**
     * Extension filtering is tested at the file level only. All three search methods
     * (searchFiles, searchSections, searchCodeSamples) delegate to FilterUtils.matchesFilter,
     * so file-level tests provide full coverage of the filtering logic.
     */
    @Nested
    class ExtensionFilteringTests {

        // --- searchFiles extension filtering ---

        @Test
        void searchFilesWithExtensionFilterReturnsOnlyMatchingFiles() {
            KeywordIndex index = new KeywordIndex(List.of(
                    new FileKeywordEntry("core.adoc",
                            List.of(new KeywordScore("secur", 10)), List.of(), "quarkus-core"),
                    new FileKeywordEntry("ext.adoc",
                            List.of(new KeywordScore("secur", 15)), List.of(), "quarkus-openapi-generator")
            ));
            seedIndex("3.27", index);

            PaginatedResult<FileSearchResult> result = searchService.searchFiles(
                    "3.27", List.of("security"), "quarkus-core", 10, 0);

            assertThat(result.items()).hasSize(1);
            assertThat(result.total()).isEqualTo(1);
            assertThat(result.items().get(0).path).isEqualTo("core.adoc");
        }

        @ParameterizedTest(name = "extension={0} returns all files")
        @NullSource
        @ValueSource(strings = {"", "  "})
        void searchFilesWithNullOrBlankExtensionReturnsAllFiles(String extension) {
            KeywordIndex index = new KeywordIndex(List.of(
                    new FileKeywordEntry("core.adoc",
                            List.of(new KeywordScore("secur", 10)), List.of(), "quarkus-core"),
                    new FileKeywordEntry("ext.adoc",
                            List.of(new KeywordScore("secur", 15)), List.of(), "quarkus-openapi-generator")
            ));
            seedIndex("3.27", index);

            PaginatedResult<FileSearchResult> result = searchService.searchFiles(
                    "3.27", List.of("security"), extension, 10, 0);

            assertThat(result.items()).hasSize(2);
            assertThat(result.total()).isEqualTo(2);
        }

        @Test
        void searchFilesWithNonexistentExtensionReturnsEmpty() {
            KeywordIndex index = new KeywordIndex(List.of(
                    new FileKeywordEntry("core.adoc",
                            List.of(new KeywordScore("secur", 10)), List.of(), "quarkus-core")
            ));
            seedIndex("3.27", index);

            PaginatedResult<FileSearchResult> result = searchService.searchFiles(
                    "3.27", List.of("security"), "nonexistent-extension", 10, 0);

            assertThat(result.items()).isEmpty();
            assertThat(result.total()).isEqualTo(0);
        }

        @Test
        void searchFilesExtensionFilterAppliesBeforePagination() {
            KeywordIndex index = new KeywordIndex(List.of(
                    new FileKeywordEntry("core1.adoc",
                            List.of(new KeywordScore("secur", 10)), List.of(), "quarkus-core"),
                    new FileKeywordEntry("ext1.adoc",
                            List.of(new KeywordScore("secur", 20)), List.of(), "quarkus-openapi-generator"),
                    new FileKeywordEntry("core2.adoc",
                            List.of(new KeywordScore("secur", 5)), List.of(), "quarkus-core")
            ));
            seedIndex("3.27", index);

            PaginatedResult<FileSearchResult> result = searchService.searchFiles(
                    "3.27", List.of("security"), "quarkus-core", 10, 0);

            assertThat(result.items()).hasSize(2);
            assertThat(result.total()).isEqualTo(2);
        }

    }

    /**
     * Helper method to extract keyword strings from MatchedKeyword list for test assertions.
     */
    private static List<String> extractKeywords(List<MatchedKeyword> matchedKeywords) {
        return matchedKeywords.stream()
                .map(MatchedKeyword::keyword)
                .toList();
    }
}
