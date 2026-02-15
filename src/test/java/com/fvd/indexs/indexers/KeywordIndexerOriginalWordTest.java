package com.fvd.indexs.indexers;

import com.fvd.asciidocs.parser.AsciidocParser;
import com.fvd.cache.services.CacheService;
import com.fvd.common.TestSqliteHelper;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.search.TestKeywordScoringConfig;
import com.fvd.search.TestSearchConfig;
import com.fvd.search.services.KeywordScorer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordIndexerOriginalWordTest {

    @TempDir
    Path tempDir;

    KeywordIndexer indexer;
    DocStore docStore;
    KeywordIndexStore keywordIndexStore;

    @BeforeEach
    void setUp() {
        CacheService cacheService = new CacheService(tempDir.toString());
        docStore = new DocStore(cacheService);
        SQLiteDataSource ds = TestSqliteHelper.createInitializedDataSource(tempDir);
        keywordIndexStore = new KeywordIndexStore(ds, new com.fvd.indexs.stores.DocumentMetadataStore(ds));
        DocParser parser = new AsciidocParser(new TestSearchConfig());
        KeywordScorer keywordScorer = new KeywordScorer(new TestKeywordScoringConfig());
        indexer = new KeywordIndexer(docStore, keywordIndexStore, parser, new TestSearchConfig(), keywordScorer);
    }

    @Test
    void originalWordIsPopulatedOnKeywordScore() {
        // Keywords must appear in headings or have sufficient frequency to pass minKeywordScore=2.
        // Title weight=8.0, section weight=5.0 → both exceed the threshold.
        docStore.write("3.27", "test.adoc", """
                = Security Guide
                
                This document covers security details.
                
                == Configuration
                
                The configuration options are described below.
                
                == Authentication
                
                Authentication is handled by the security module.
                """);

        KeywordIndex index = indexer.build("3.27", List.of("test.adoc"));
        FileKeywordEntry entry = index.files.get(0);

        // "security" -> "secur" (appears in title + body → title source, score >= 8)
        Optional<KeywordScore> securityScore = entry.keywords.stream()
                .filter(k -> k.word.equals("secur")).findFirst();
        assertThat(securityScore).isPresent();
        assertThat(securityScore.get().originalWord).isNotNull();
        assertThat(securityScore.get().originalWord).isEqualTo("security");

        // "configuration" -> "configur" (appears in section heading → section source, score >= 5)
        Optional<KeywordScore> configScore = entry.keywords.stream()
                .filter(k -> k.word.equals("configur")).findFirst();
        assertThat(configScore).isPresent();
        assertThat(configScore.get().originalWord).isEqualTo("configuration");

        // "authentication" -> "authentic" (appears in section heading → section source, score >= 5)
        Optional<KeywordScore> authScore = entry.keywords.stream()
                .filter(k -> k.word.equals("authentic")).findFirst();
        assertThat(authScore).isPresent();
        assertThat(authScore.get().originalWord).isEqualTo("authentication");
    }

    @Test
    void longestOriginalWinsWhenMultipleTokensStemToSameForm() {
        // "configuring" (11 chars) and "configuration" (13 chars) both stem to "configur"
        docStore.write("3.27", "test.adoc", """
                = Guide
                
                Configuring the configuration for services.
                """);

        KeywordIndex index = indexer.build("3.27", List.of("test.adoc"));
        FileKeywordEntry entry = index.files.get(0);

        Optional<KeywordScore> configScore = entry.keywords.stream()
                .filter(k -> k.word.equals("configur")).findFirst();
        assertThat(configScore).isPresent();
        // "configuration" (13 chars) is longer than "configuring" (11 chars)
        assertThat(configScore.get().originalWord).isEqualTo("configuration");
    }

    @Test
    void filenameBoostKeywordsHaveOriginals() {
        docStore.write("3.27", "security-configuration.adoc", """
                = Security Configuration Guide
                
                Content about security configuration.
                """);

        KeywordIndex index = indexer.build("3.27", List.of("security-configuration.adoc"));
        FileKeywordEntry entry = index.files.get(0);

        Optional<KeywordScore> securityScore = entry.keywords.stream()
                .filter(k -> k.word.equals("secur")).findFirst();
        assertThat(securityScore).isPresent();
        assertThat(securityScore.get().originalWord).isNotNull();
        assertThat(securityScore.get().originalWord).isNotEmpty();
        assertThat(securityScore.get().source).isEqualTo("filename");
    }

    @Test
    void headingKeywordsHaveOriginals() {
        docStore.write("3.27", "test.adoc", """
                = Authentication Overview
                
                Some content about authentication.
                
                == Database Configuration
                
                The database connection configuration.
                """);

        KeywordIndex index = indexer.build("3.27", List.of("test.adoc"));
        FileKeywordEntry entry = index.files.get(0);

        // "authentication" -> "authentic" from title
        Optional<KeywordScore> authScore = entry.keywords.stream()
                .filter(k -> k.word.equals("authentic")).findFirst();
        assertThat(authScore).isPresent();
        assertThat(authScore.get().originalWord).isEqualTo("authentication");

        // "database" stays as "database" (no stem rule matches)
        Optional<KeywordScore> dbScore = entry.keywords.stream()
                .filter(k -> k.word.equals("database")).findFirst();
        assertThat(dbScore).isPresent();
        assertThat(dbScore.get().originalWord).isEqualTo("database");
    }

    @Test
    void sectionKeywordsHaveOriginals() {
        docStore.write("3.27", "test.adoc", """
                = Title
                
                == Security Configuration
                
                This section explains security configuration details.
                """);

        KeywordIndex index = indexer.build("3.27", List.of("test.adoc"));
        FileKeywordEntry entry = index.files.get(0);

        // Check section-level keywords
        Optional<SectionKeywordEntry> section = entry.sections.stream()
                .filter(s -> s.title.equals("Security Configuration")).findFirst();
        assertThat(section).isPresent();

        Optional<KeywordScore> secScore = section.get().keywords.stream()
                .filter(k -> k.word.equals("secur")).findFirst();
        assertThat(secScore).isPresent();
        assertThat(secScore.get().originalWord).isNotNull();
        assertThat(secScore.get().originalWord).isNotEmpty();
    }

    @Test
    void originalWordPersistedAndLoadedFromStore() {
        // Keywords must appear in headings to exceed minKeywordScore=2 threshold.
        docStore.write("3.27", "security-configuration.adoc", """
                = Security Guide
                
                This section covers security for services.
                
                == Configuration
                
                The configuration settings are documented below.
                
                == Authentication
                
                Authentication is handled by the security module.
                """);

        indexer.build("3.27", List.of("security-configuration.adoc"));

        // Read back from the store
        Optional<KeywordIndex> stored = keywordIndexStore.read("3.27");
        assertThat(stored).isPresent();

        FileKeywordEntry entry = stored.get().files.get(0);
        Optional<KeywordScore> securityScore = entry.keywords.stream()
                .filter(k -> k.word.equals("secur")).findFirst();
        assertThat(securityScore).isPresent();
        assertThat(securityScore.get().originalWord).isEqualTo("security");

        Optional<KeywordScore> configScore = entry.keywords.stream()
                .filter(k -> k.word.equals("configur")).findFirst();
        assertThat(configScore).isPresent();
        assertThat(configScore.get().originalWord).isEqualTo("configuration");
    }

    @Test
    void unstemmedWordsHaveOriginalMatchingStemmed() {
        // Use section headings to boost keywords above minKeywordScore=2 threshold.
        // Words that don't change under stemming should have original == word.
        docStore.write("3.27", "oidc-rest-database.adoc", """
                = OIDC REST Database
                
                Content about oidc and rest and database endpoints.
                """);

        KeywordIndex index = indexer.build("3.27", List.of("oidc-rest-database.adoc"));
        FileKeywordEntry entry = index.files.get(0);

        // "oidc" → stemmed "oidc" (no rule matches), boosted by filename+title
        Optional<KeywordScore> oidcScore = entry.keywords.stream()
                .filter(k -> k.word.equals("oidc")).findFirst();
        assertThat(oidcScore).isPresent();
        assertThat(oidcScore.get().originalWord).isEqualTo("oidc");

        // "rest" → stemmed "rest" (no rule matches), boosted by filename+title
        Optional<KeywordScore> restScore = entry.keywords.stream()
                .filter(k -> k.word.equals("rest")).findFirst();
        assertThat(restScore).isPresent();
        assertThat(restScore.get().originalWord).isEqualTo("rest");

        // "database" → stemmed "database" (no rule matches), boosted by filename+title
        Optional<KeywordScore> dbScore = entry.keywords.stream()
                .filter(k -> k.word.equals("database")).findFirst();
        assertThat(dbScore).isPresent();
        assertThat(dbScore.get().originalWord).isEqualTo("database");
    }

    @Test
    void backwardCompatibleConstructorSetsOriginalToWord() {
        KeywordScore score = new KeywordScore("configur", 10);
        assertThat(score.originalWord).isEqualTo("configur");

        KeywordScore score2 = new KeywordScore("secur", 5, "body", 1);
        assertThat(score2.originalWord).isEqualTo("secur");
    }
}
