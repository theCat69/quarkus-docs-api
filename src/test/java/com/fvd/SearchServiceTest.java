package com.fvd;

import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class SearchServiceTest {

    @TempDir
    Path tempDir;

    SearchService searchService;
    KeywordIndexStore keywordIndexStore;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        CacheService cacheService = new CacheService(tempDir.toString());
        keywordIndexStore = new KeywordIndexStore(cacheService);
        objectMapper = new ObjectMapper();
        searchService = new SearchService(keywordIndexStore, objectMapper);
    }

    private void seedIndex(String version, KeywordIndex index) throws Exception {
        String json = objectMapper.writeValueAsString(index);
        keywordIndexStore.write(version, json);
    }

    // --- File search tests ---

    @Test
    void searchFilesReturnsSortedByDescendingScore() throws Exception {
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
    void searchFilesAggregatesScoresAcrossKeywords() throws Exception {
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
    void searchFilesMultiKeywordBoostIncreasesScore() throws Exception {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("multi.adoc",
                        List.of(new KeywordScore("security", 5), new KeywordScore("oidc", 5)), List.of()),
                new FileKeywordEntry("single.adoc",
                        List.of(new KeywordScore("security", 10)), List.of())
        ));
        seedIndex("3.21", index);

        List<FileSearchResult> results = searchService.searchFiles("3.21", List.of("security", "oidc"));

        // multi.adoc: 5 + 5 + boost for matching 2 keywords
        // single.adoc: 10 (no boost, only 1 keyword matched)
        FileSearchResult multiResult = results.stream()
                .filter(r -> r.path.equals("multi.adoc")).findFirst().orElseThrow();
        FileSearchResult singleResult = results.stream()
                .filter(r -> r.path.equals("single.adoc")).findFirst().orElseThrow();
        assertThat(multiResult.score).isGreaterThan(singleResult.score);
    }

    @Test
    void searchFilesLimitsResultsToTen() throws Exception {
        List<FileKeywordEntry> files = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            files.add(new FileKeywordEntry("file" + i + ".adoc",
                    List.of(new KeywordScore("test", 15 - i)), List.of()));
        }
        seedIndex("3.21", new KeywordIndex(files));

        List<FileSearchResult> results = searchService.searchFiles("3.21", List.of("test"));

        assertThat(results).hasSize(10);
        // Should be the top-10 by score
        assertThat(results.get(0).path).isEqualTo("file0.adoc");
        assertThat(results.get(9).path).isEqualTo("file9.adoc");
    }

    @Test
    void searchFilesReturnsEmptyForUnknownKeyword() throws Exception {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc",
                        List.of(new KeywordScore("security", 10)), List.of())
        ));
        seedIndex("3.21", index);

        List<FileSearchResult> results = searchService.searchFiles("3.21", List.of("nonexistent"));

        assertThat(results).isEmpty();
    }

    @Test
    void searchFilesReturnsEmptyWhenNoIndexExists() {
        List<FileSearchResult> results = searchService.searchFiles("3.21", List.of("security"));

        assertThat(results).isEmpty();
    }

    // --- Section search tests ---

    @Test
    void searchSectionsReturnsMatchingSections() throws Exception {
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
    void searchSectionsFiltersToProvidedFilePaths() throws Exception {
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
    void searchSectionsLimitsResultsToFive() throws Exception {
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
    void searchSectionsReturnsEmptyForUnmatchedKeyword() throws Exception {
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
    void searchSectionsReturnsEmptyWhenNoIndexExists() {
        List<SectionSearchResult> results = searchService.searchSections(
                "3.21", List.of("security"), List.of("test.adoc"));

        assertThat(results).isEmpty();
    }
}
