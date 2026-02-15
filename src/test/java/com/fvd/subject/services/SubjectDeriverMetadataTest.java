package com.fvd.subject.services;

import com.fvd.asciidocs.model.DocumentMetadata;
import com.fvd.subject.SubjectConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubjectDeriverMetadataTest {

    private SubjectDeriver subjectDeriver;
    private SubjectConfig config;

    @BeforeEach
    void setUp() {
        config = mock(SubjectConfig.class);
        when(config.enabled()).thenReturn(true);
        when(config.caseInsensitive()).thenReturn(true);
        when(config.overrides()).thenReturn(Map.of());
        when(config.globOverrides()).thenReturn(Map.of());
        when(config.patterns()).thenReturn(List.of());
        when(config.definitions()).thenReturn(Map.of());

        subjectDeriver = new SubjectDeriver(config);
        subjectDeriver.init();
    }

    // --- mapCategoryToSubject tests ---

    @Nested
    class CategoryMappingTests {

        @Test
        void knownCategoryReturnsMappedSubject() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("security"))
                    .topics(List.of())
                    .build();

            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("security");
        }

        @Test
        void webCategoryMapsToRestApis() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("web"))
                    .topics(List.of())
                    .build();

            assertThat(subjectDeriver.deriveSubject("virtual-threads.adoc", metadata))
                    .isEqualTo("rest-apis");
        }

        @Test
        void dataCategoryMapsToDataPersistence() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("data"))
                    .topics(List.of())
                    .build();

            assertThat(subjectDeriver.deriveSubject("hibernate-orm.adoc", metadata))
                    .isEqualTo("data-persistence");
        }

        @Test
        void coreCategoryMapsToCoreConcepts() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("core"))
                    .topics(List.of())
                    .build();

            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("core-concepts");
        }

        @Test
        void writingExtensionsCategoryMapsToExtensions() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("writing-extensions"))
                    .topics(List.of())
                    .build();

            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("extensions");
        }

        @Test
        void businessAutomationCategoryMapsToExtensions() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("business-automation"))
                    .topics(List.of())
                    .build();

            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("extensions");
        }

        @Test
        void firstCategoryWinsWhenMultiple() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("security", "web"))
                    .topics(List.of())
                    .build();

            assertThat(subjectDeriver.deriveSubject("security-oidc.adoc", metadata))
                    .isEqualTo("security");
        }

        @Test
        void unknownCategoryReturnsNull_fallsToRegexOrMisc() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("unknown-category"))
                    .topics(List.of())
                    .build();

            // "some-random-doc.adoc" doesn't match any regex either
            assertThat(subjectDeriver.deriveSubject("some-random-doc.adoc", metadata))
                    .isEqualTo("misc");
        }

        @Test
        void mixedKnownAndUnknownCategoriesUsesFirstKnown() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("unknown", "web"))
                    .topics(List.of())
                    .build();

            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("rest-apis");
        }

        @Test
        void emptyCategoriesFallsThrough() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of())
                    .topics(List.of())
                    .build();

            assertThat(subjectDeriver.deriveSubject("some-random-doc.adoc", metadata))
                    .isEqualTo("misc");
        }

        @Test
        void categoryMatchIsCaseInsensitive() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("SECURITY"))
                    .topics(List.of())
                    .build();

            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("security");
        }

        @Test
        void categoryMatchTrimsWhitespace() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("  web  "))
                    .topics(List.of())
                    .build();

            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("rest-apis");
        }
    }

    // --- mapTopicsToSubject tests ---

    @Nested
    class TopicMappingTests {

        @Test
        void singleMatchingTopicReturnsSubject() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of())
                    .topics(List.of("rest"))
                    .build();

            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("rest-apis");
        }

        @Test
        void multipleMatchingTopicsSameSubjectReturnsThatSubject() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of())
                    .topics(List.of("rest", "resteasy"))
                    .build();

            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("rest-apis");
        }

        @Test
        void majorityVoteSelectsSubjectWithMostVotes() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of())
                    .topics(List.of("security", "oidc", "rest"))
                    .build();

            // security=2 votes (security, oidc), rest-apis=1 vote (rest)
            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("security");
        }

        @Test
        void majorityVoteWithThreeVotes() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of())
                    .topics(List.of("security", "oidc", "jwt"))
                    .build();

            // security=3 votes
            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("security");
        }

        @Test
        void tieBreakingFirstInsertedWins() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of())
                    .topics(List.of("rest", "kafka"))
                    .build();

            // rest-apis=1 vote, messaging=1 vote — rest appears first, so rest-apis wins
            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("rest-apis");
        }

        @Test
        void tieBreakingReversedOrder() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of())
                    .topics(List.of("kafka", "rest"))
                    .build();

            // messaging=1 vote, rest-apis=1 vote — kafka appears first, so messaging wins
            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("messaging");
        }

        @Test
        void noMatchingTopicsReturnsNull_fallsToRegexOrMisc() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of())
                    .topics(List.of("unknown-topic", "another-unknown"))
                    .build();

            assertThat(subjectDeriver.deriveSubject("some-random-doc.adoc", metadata))
                    .isEqualTo("misc");
        }

        @Test
        void emptyTopicsFallsThrough() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of())
                    .topics(List.of())
                    .build();

            assertThat(subjectDeriver.deriveSubject("some-random-doc.adoc", metadata))
                    .isEqualTo("misc");
        }

        @Test
        void topicMatchIsCaseInsensitive() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of())
                    .topics(List.of("KAFKA"))
                    .build();

            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("messaging");
        }

        @Test
        void topicMatchTrimsWhitespace() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of())
                    .topics(List.of("  hibernate  "))
                    .build();

            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("data-persistence");
        }
    }

    // --- deriveSubject(filePath, metadata) priority chain tests ---

    @Nested
    class PriorityChainTests {

        @Test
        void exactOverrideBeatsCategoryMetadata() {
            when(config.overrides()).thenReturn(Map.of(
                    "getting-started-guide.adoc", "getting-started"
            ));

            SubjectDeriver deriver = new SubjectDeriver(config);
            deriver.init();

            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("security"))
                    .topics(List.of())
                    .build();

            assertThat(deriver.deriveSubject("getting-started-guide.adoc", metadata))
                    .isEqualTo("getting-started");
        }

        @Test
        void categoryBeatsTopics() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("security"))
                    .topics(List.of("rest", "resteasy", "http"))
                    .build();

            // Category "security" should win over topics that all point to "rest-apis"
            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("security");
        }

        @Test
        void topicsBeatsRegex() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of())
                    .topics(List.of("kafka"))
                    .build();

            // Path "security-oidc.adoc" would match regex for "security",
            // but topics should take priority
            assertThat(subjectDeriver.deriveSubject("security-oidc.adoc", metadata))
                    .isEqualTo("messaging");
        }

        @Test
        void regexFallbackWhenNoMetadataMatch() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("unknown"))
                    .topics(List.of("unknown-topic"))
                    .build();

            // No category/topic match, but "security-oidc.adoc" matches regex
            assertThat(subjectDeriver.deriveSubject("security-oidc.adoc", metadata))
                    .isEqualTo("security");
        }

        @Test
        void nullMetadataFallsToRegex() {
            assertThat(subjectDeriver.deriveSubject("security-oidc.adoc", null))
                    .isEqualTo("security");
        }

        @Test
        void nullMetadataFallsToMiscWhenNoRegexMatch() {
            assertThat(subjectDeriver.deriveSubject("quarkiverse-doc.adoc", null))
                    .isEqualTo("extensions");
        }

        @Test
        void emptyMetadataFallsToRegex() {
            DocumentMetadata emptyMeta = DocumentMetadata.empty();

            assertThat(subjectDeriver.deriveSubject("security-oidc.adoc", emptyMeta))
                    .isEqualTo("security");
        }

        @Test
        void emptyMetadataFallsToMiscWhenNoRegexMatch() {
            DocumentMetadata emptyMeta = DocumentMetadata.empty();

            assertThat(subjectDeriver.deriveSubject("some-random-doc.adoc", emptyMeta))
                    .isEqualTo("misc");
        }

        @Test
        void nullPathReturnsMiscWithMetadata() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("security"))
                    .topics(List.of())
                    .build();

            assertThat(subjectDeriver.deriveSubject(null, metadata))
                    .isEqualTo("misc");
        }

        @Test
        void blankPathReturnsMiscWithMetadata() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("security"))
                    .topics(List.of())
                    .build();

            assertThat(subjectDeriver.deriveSubject("  ", metadata))
                    .isEqualTo("misc");
        }

        @Test
        void disabledReturnsMiscWithMetadata() {
            when(config.enabled()).thenReturn(false);

            SubjectDeriver disabledDeriver = new SubjectDeriver(config);
            disabledDeriver.init();

            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("security"))
                    .topics(List.of())
                    .build();

            assertThat(disabledDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("misc");
        }
    }

    // --- deriveSubjects(filePaths, metadataByPath) batch tests ---

    @Nested
    class BatchDerivationTests {

        @Test
        void deriveSubjectsBatchWithMetadataMap() {
            List<String> paths = List.of(
                    "security-doc.adoc",
                    "web-doc.adoc",
                    "unknown-doc.adoc"
            );

            Map<String, DocumentMetadata> metadataMap = Map.of(
                    "security-doc.adoc", DocumentMetadata.builder()
                            .categories(List.of("security"))
                            .topics(List.of())
                            .build(),
                    "web-doc.adoc", DocumentMetadata.builder()
                            .categories(List.of("web"))
                            .topics(List.of())
                            .build()
            );

            Map<String, String> result = subjectDeriver.deriveSubjects(paths, metadataMap);

            assertThat(result).hasSize(3);
            assertThat(result.get("security-doc.adoc")).isEqualTo("security");
            assertThat(result.get("web-doc.adoc")).isEqualTo("rest-apis");
            assertThat(result.get("unknown-doc.adoc")).isEqualTo("misc");
        }

        @Test
        void deriveSubjectsBatchWithMixedMetadata() {
            List<String> paths = List.of(
                    "category-doc.adoc",
                    "topic-doc.adoc",
                    "regex-doc.adoc",
                    "no-match-doc.adoc"
            );

            Map<String, DocumentMetadata> metadataMap = Map.of(
                    "category-doc.adoc", DocumentMetadata.builder()
                            .categories(List.of("data"))
                            .topics(List.of())
                            .build(),
                    "topic-doc.adoc", DocumentMetadata.builder()
                            .categories(List.of())
                            .topics(List.of("kubernetes", "docker"))
                            .build(),
                    "regex-doc.adoc", DocumentMetadata.builder()
                            .categories(List.of())
                            .topics(List.of())
                            .build()
            );

            Map<String, String> result = subjectDeriver.deriveSubjects(paths, metadataMap);

            assertThat(result).hasSize(4);
            assertThat(result.get("category-doc.adoc")).isEqualTo("data-persistence");
            assertThat(result.get("topic-doc.adoc")).isEqualTo("cloud");
            assertThat(result.get("regex-doc.adoc")).isEqualTo("misc");
            assertThat(result.get("no-match-doc.adoc")).isEqualTo("misc");
        }

        @Test
        void deriveSubjectsBatchWithEmptyMetadataMap() {
            List<String> paths = List.of("security-oidc.adoc", "some-random.adoc");

            Map<String, String> result = subjectDeriver.deriveSubjects(paths, Map.of());

            assertThat(result).hasSize(2);
            assertThat(result.get("security-oidc.adoc")).isEqualTo("security");
            assertThat(result.get("some-random.adoc")).isEqualTo("misc");
        }
    }

    // --- All 16 category mappings ---

    @Nested
    class AllCategoryMappingsTests {

        @Test
        void allCategoryMappingsAreCorrect() {
            Map<String, String> expectedMappings = Map.ofEntries(
                    Map.entry("getting-started", "getting-started"),
                    Map.entry("core", "core-concepts"),
                    Map.entry("web", "rest-apis"),
                    Map.entry("data", "data-persistence"),
                    Map.entry("security", "security"),
                    Map.entry("messaging", "messaging"),
                    Map.entry("cloud", "cloud"),
                    Map.entry("observability", "observability"),
                    Map.entry("tooling", "tooling"),
                    Map.entry("compatibility", "core-concepts"),
                    Map.entry("writing-extensions", "extensions"),
                    Map.entry("miscellaneous", "misc"),
                    Map.entry("integration", "messaging"),
                    Map.entry("serialization", "rest-apis"),
                    Map.entry("alternative-languages", "core-concepts"),
                    Map.entry("business-automation", "extensions")
            );

            for (Map.Entry<String, String> entry : expectedMappings.entrySet()) {
                DocumentMetadata metadata = DocumentMetadata.builder()
                        .categories(List.of(entry.getKey()))
                        .topics(List.of())
                        .build();

                assertThat(subjectDeriver.deriveSubject("test-doc.adoc", metadata))
                        .as("Category '%s' should map to '%s'", entry.getKey(), entry.getValue())
                        .isEqualTo(entry.getValue());
            }
        }
    }

    // --- Acceptance criteria from the spec ---

    @Nested
    class AcceptanceCriteriaTests {

        @Test
        void ac1_securityCategoryWithMultipleCategories() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("security", "web"))
                    .topics(List.of())
                    .build();

            assertThat(subjectDeriver.deriveSubject("security-oidc.adoc", metadata))
                    .isEqualTo("security");
        }

        @Test
        void ac2_webCategoryForVirtualThreads() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("web"))
                    .topics(List.of())
                    .build();

            assertThat(subjectDeriver.deriveSubject("virtual-threads.adoc", metadata))
                    .isEqualTo("rest-apis");
        }

        @Test
        void ac3_dataCategoryForHibernate() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("data"))
                    .topics(List.of())
                    .build();

            assertThat(subjectDeriver.deriveSubject("hibernate-orm.adoc", metadata))
                    .isEqualTo("data-persistence");
        }

        @Test
        void ac4_topicsRestResteasyReactive() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of())
                    .topics(List.of("rest", "resteasy-reactive"))
                    .build();

            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("rest-apis");
        }

        @Test
        void ac5_topicsMajorityVoteSecurity() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of())
                    .topics(List.of("security", "oidc", "jwt"))
                    .build();

            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("security");
        }

        @Test
        void ac6_deterministicTieBreaking() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of())
                    .topics(List.of("rest", "kafka"))
                    .build();

            assertThat(subjectDeriver.deriveSubject("some-doc.adoc", metadata))
                    .isEqualTo("rest-apis");
        }

        @Test
        void ac7_nullMetadataFallsToRegex() {
            assertThat(subjectDeriver.deriveSubject("quarkiverse-doc.adoc", null))
                    .isEqualTo("extensions");
        }

        @Test
        void ac8_emptyMetadataFallsToRegex() {
            assertThat(subjectDeriver.deriveSubject("quarkiverse-doc.adoc", DocumentMetadata.empty()))
                    .isEqualTo("extensions");
        }

        @Test
        void ac9_exactOverrideTakesPriorityOverMetadata() {
            when(config.overrides()).thenReturn(Map.of(
                    "getting-started-guide.adoc", "getting-started"
            ));

            SubjectDeriver deriver = new SubjectDeriver(config);
            deriver.init();

            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("security"))
                    .topics(List.of())
                    .build();

            assertThat(deriver.deriveSubject("getting-started-guide.adoc", metadata))
                    .isEqualTo("getting-started");
        }
    }
}
