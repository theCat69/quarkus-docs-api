package com.fvd.subject.services;

import com.fvd.subject.Subject;
import com.fvd.subject.SubjectConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubjectDeriverTest {

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

    // --- Pattern matching tests ---

    @Test
    void deriveSubjectReturnsGettingStartedForQuickstart() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/getting-started.adoc"))
                .isEqualTo("getting-started");
    }

    @Test
    void deriveSubjectReturnsGettingStartedForTutorial() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/tutorial-getting-started.adoc"))
                .isEqualTo("getting-started");
    }

    @Test
    void deriveSubjectReturnsSecurityForOidc() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/security-oidc.adoc"))
                .isEqualTo("security");
    }

    @Test
    void deriveSubjectReturnsSecurityForJwt() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/security-jwt.adoc"))
                .isEqualTo("security");
    }

    @Test
    void deriveSubjectReturnsSecurityForKeycloak() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/keycloak-admin-client.adoc"))
                .isEqualTo("security");
    }

    @Test
    void deriveSubjectReturnsRestApisForRest() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/rest-client.adoc"))
                .isEqualTo("rest-apis");
    }

    @Test
    void deriveSubjectReturnsRestApisForResteasy() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/resteasy-reactive.adoc"))
                .isEqualTo("rest-apis");
    }

    @Test
    void deriveSubjectReturnsDataPersistenceForHibernate() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/hibernate-orm.adoc"))
                .isEqualTo("data-persistence");
    }

    @Test
    void deriveSubjectReturnsDataPersistenceForPanache() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/hibernate-orm-panache.adoc"))
                .isEqualTo("data-persistence");
    }

    @Test
    void deriveSubjectReturnsDataPersistenceForDatasource() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/datasource.adoc"))
                .isEqualTo("data-persistence");
    }

    @Test
    void deriveSubjectReturnsCoreConceptsForCdi() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/cdi.adoc"))
                .isEqualTo("core-concepts");
    }

    @Test
    void deriveSubjectReturnsCoreConceptsForConfiguration() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/configuration.adoc"))
                .isEqualTo("core-concepts");
    }

    @Test
    void deriveSubjectReturnsCoreConceptsForConfig() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/config.adoc"))
                .isEqualTo("core-concepts");
    }

    @Test
    void deriveSubjectReturnsMessagingForKafka() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/kafka.adoc"))
                .isEqualTo("messaging");
    }

    @Test
    void deriveSubjectReturnsMessagingForAmqp() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/amqp.adoc"))
                .isEqualTo("messaging");
    }

    @Test
    void deriveSubjectReturnsCloudForKubernetes() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/kubernetes.adoc"))
                .isEqualTo("cloud");
    }

    @Test
    void deriveSubjectReturnsCloudForDocker() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/container-image-docker.adoc"))
                .isEqualTo("cloud");
    }

    @Test
    void deriveSubjectReturnsCloudForOpenshift() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/openshift.adoc"))
                .isEqualTo("cloud");
    }

    @Test
    void deriveSubjectReturnsObservabilityForMetrics() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/metrics.adoc"))
                .isEqualTo("observability");
    }

    @Test
    void deriveSubjectReturnsObservabilityForHealth() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/health.adoc"))
                .isEqualTo("observability");
    }

    @Test
    void deriveSubjectReturnsObservabilityForTracing() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/opentelemetry.adoc"))
                .isEqualTo("observability");
    }

    @Test
    void deriveSubjectReturnsTestingForTest() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/test.adoc"))
                .isEqualTo("testing");
    }

    @Test
    void deriveSubjectReturnsTestingForJunit() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/junit.adoc"))
                .isEqualTo("testing");
    }

    @Test
    void deriveSubjectReturnsToolingForCli() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/cli.adoc"))
                .isEqualTo("tooling");
    }

    @Test
    void deriveSubjectReturnsToolingForDevServices() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/dev-services.adoc"))
                .isEqualTo("tooling");
    }

    @Test
    void deriveSubjectReturnsToolingForMaven() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/maven-tooling.adoc"))
                .isEqualTo("tooling");
    }

    @Test
    void deriveSubjectReturnsToolingForGradle() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/gradle-tooling.adoc"))
                .isEqualTo("tooling");
    }

    @Test
    void deriveSubjectReturnsExtensionsForExtension() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/extension-development.adoc"))
                .isEqualTo("extensions");
    }

    @Test
    void deriveSubjectReturnsExtensionsForQuarkiverse() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/quarkiverse.adoc"))
                .isEqualTo("extensions");
    }

    @Test
    void deriveSubjectReturnsMiscForUnmatchedPath() {
        assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/some-random-topic.adoc"))
                .isEqualTo("misc");
    }

    // --- Edge cases ---

    @Test
    void deriveSubjectReturnsMiscForNull() {
        assertThat(subjectDeriver.deriveSubject(null)).isEqualTo("misc");
    }

    @Test
    void deriveSubjectReturnsMiscForEmpty() {
        assertThat(subjectDeriver.deriveSubject("")).isEqualTo("misc");
    }

    @Test
    void deriveSubjectReturnsMiscForBlank() {
        assertThat(subjectDeriver.deriveSubject("   ")).isEqualTo("misc");
    }

    @Test
    void deriveSubjectIsCaseInsensitive() {
        assertThat(subjectDeriver.deriveSubject("docs/SECURITY-OIDC.adoc"))
                .isEqualTo("security");
    }

    @Test
    void deriveSubjectNormalizesBackslashes() {
        assertThat(subjectDeriver.deriveSubject("docs\\src\\main\\asciidoc\\security-oidc.adoc"))
                .isEqualTo("security");
    }

    // --- Override tests ---

    @Test
    void deriveSubjectUsesExactOverride() {
        when(config.overrides()).thenReturn(Map.of("docs/special-file.adoc", "special-subject"));
        
        SubjectDeriver deriver = new SubjectDeriver(config);
        deriver.init();

        assertThat(deriver.deriveSubject("docs/special-file.adoc")).isEqualTo("special-subject");
    }

    @Test
    void deriveSubjectPrioritizesOverridesOverPatterns() {
        when(config.overrides()).thenReturn(Map.of("docs/security.adoc", "custom-subject"));
        
        SubjectDeriver deriver = new SubjectDeriver(config);
        deriver.init();

        // Would normally match "security" pattern, but override takes precedence
        assertThat(deriver.deriveSubject("docs/security.adoc")).isEqualTo("custom-subject");
    }

    // --- Configured patterns tests ---

    @Test
    void deriveSubjectUsesConfiguredPatterns() {
        SubjectConfig.SubjectPattern customPattern = mock(SubjectConfig.SubjectPattern.class);
        when(customPattern.pattern()).thenReturn(".*/custom-topic.*");
        when(customPattern.subject()).thenReturn("custom-subject");
        when(config.patterns()).thenReturn(List.of(customPattern));

        SubjectDeriver deriver = new SubjectDeriver(config);
        deriver.init();

        assertThat(deriver.deriveSubject("docs/custom-topic.adoc")).isEqualTo("custom-subject");
    }

    @Test
    void deriveSubjectEvaluatesPatternsInOrder() {
        // Both patterns match the same path, but first pattern should win
        SubjectConfig.SubjectPattern pattern1 = mock(SubjectConfig.SubjectPattern.class);
        when(pattern1.pattern()).thenReturn(".*first.*");
        when(pattern1.subject()).thenReturn("first-subject");

        SubjectConfig.SubjectPattern pattern2 = mock(SubjectConfig.SubjectPattern.class);
        when(pattern2.pattern()).thenReturn(".*match.*");
        when(pattern2.subject()).thenReturn("second-subject");

        when(config.patterns()).thenReturn(List.of(pattern1, pattern2));

        SubjectDeriver deriver = new SubjectDeriver(config);
        deriver.init();

        // Both patterns match "first-match", but first pattern should win
        assertThat(deriver.deriveSubject("docs/first-match.adoc")).isEqualTo("first-subject");
    }

    // --- Multiple file derivation ---

    @Test
    void deriveSubjectsReturnsMapOfSubjects() {
        List<String> paths = List.of(
                "docs/security-oidc.adoc",
                "docs/rest-client.adoc",
                "docs/unknown.adoc"
        );

        Map<String, String> result = subjectDeriver.deriveSubjects(paths);

        assertThat(result).hasSize(3);
        assertThat(result.get("docs/security-oidc.adoc")).isEqualTo("security");
        assertThat(result.get("docs/rest-client.adoc")).isEqualTo("rest-apis");
        assertThat(result.get("docs/unknown.adoc")).isEqualTo("misc");
    }

    // --- Subject metadata tests ---

    @Test
    void getAllSubjectsReturnsAllDefinedSubjects() {
        List<Subject> subjects = subjectDeriver.getAllSubjects();

        assertThat(subjects).isNotEmpty();
        assertThat(subjects).extracting(Subject::name)
                .contains("getting-started", "core-concepts", "rest-apis", "data-persistence",
                        "security", "messaging", "cloud", "observability", "testing",
                        "tooling", "extensions", "misc");
    }

    @Test
    void getAllSubjectsIncludesDisplayNames() {
        List<Subject> subjects = subjectDeriver.getAllSubjects();

        Subject security = subjects.stream()
                .filter(s -> "security".equals(s.name()))
                .findFirst()
                .orElseThrow();

        assertThat(security.displayName()).isEqualTo("Security");
    }

    @Test
    void getAllSubjectsIncludesDescriptions() {
        List<Subject> subjects = subjectDeriver.getAllSubjects();

        Subject security = subjects.stream()
                .filter(s -> "security".equals(s.name()))
                .findFirst()
                .orElseThrow();

        assertThat(security.description()).isNotEmpty();
    }

    @Test
    void getAllSubjectsIncludesKeywords() {
        List<Subject> subjects = subjectDeriver.getAllSubjects();

        Subject security = subjects.stream()
                .filter(s -> "security".equals(s.name()))
                .findFirst()
                .orElseThrow();

        assertThat(security.keywords()).contains("security", "oidc", "jwt");
    }

    @Test
    void getSubjectReturnsSubjectByName() {
        Optional<Subject> subject = subjectDeriver.getSubject("security");

        assertThat(subject).isPresent();
        assertThat(subject.get().name()).isEqualTo("security");
        assertThat(subject.get().displayName()).isEqualTo("Security");
    }

    @Test
    void getSubjectReturnsEmptyForUnknownName() {
        Optional<Subject> subject = subjectDeriver.getSubject("unknown-subject");

        assertThat(subject).isEmpty();
    }

    @Test
    void getSubjectReturnsEmptyForNull() {
        Optional<Subject> subject = subjectDeriver.getSubject(null);

        assertThat(subject).isEmpty();
    }

    @Test
    void getSubjectReturnsEmptyForBlank() {
        Optional<Subject> subject = subjectDeriver.getSubject("  ");

        assertThat(subject).isEmpty();
    }

    // --- Document count tracking ---

    @Test
    void recordDocumentTracksCount() {
        subjectDeriver.recordDocument("security");
        subjectDeriver.recordDocument("security");
        subjectDeriver.recordDocument("rest-apis");

        Optional<Subject> security = subjectDeriver.getSubject("security");
        Optional<Subject> restApis = subjectDeriver.getSubject("rest-apis");

        assertThat(security).isPresent();
        assertThat(security.get().docCount()).isEqualTo(2);
        assertThat(restApis).isPresent();
        assertThat(restApis.get().docCount()).isEqualTo(1);
    }

    @Test
    void resetDocCountsClearsAllCounts() {
        subjectDeriver.recordDocument("security");
        subjectDeriver.recordDocument("security");
        subjectDeriver.resetDocCounts();

        Optional<Subject> security = subjectDeriver.getSubject("security");

        assertThat(security).isPresent();
        assertThat(security.get().docCount()).isEqualTo(0);
    }

    @Test
    void getSubjectsWithDocsReturnsOnlyPopulatedSubjects() {
        subjectDeriver.recordDocument("security");
        subjectDeriver.recordDocument("rest-apis");

        List<Subject> subjects = subjectDeriver.getSubjectsWithDocs();

        assertThat(subjects).hasSize(2);
        assertThat(subjects).extracting(Subject::name)
                .containsExactlyInAnyOrder("security", "rest-apis");
    }

    @Test
    void getSubjectsWithDocsReturnsEmptyWhenNoDocuments() {
        List<Subject> subjects = subjectDeriver.getSubjectsWithDocs();

        assertThat(subjects).isEmpty();
    }

    // --- Case sensitivity tests ---

    @Test
    void caseSensitiveMatchingWhenConfigured() {
        when(config.caseInsensitive()).thenReturn(false);
        
        SubjectDeriver caseSensitiveDeriver = new SubjectDeriver(config);
        caseSensitiveDeriver.init();

        // Lowercase should still match since patterns are lowercase
        assertThat(caseSensitiveDeriver.deriveSubject("docs/security.adoc"))
                .isEqualTo("security");
        
        // Uppercase path won't match lowercase pattern in case-sensitive mode
        assertThat(caseSensitiveDeriver.deriveSubject("docs/SECURITY.adoc"))
                .isEqualTo("misc");
    }

    @Test
    void deriveSubjectReturnsMiscWhenDisabled() {
        when(config.enabled()).thenReturn(false);
        
        SubjectDeriver disabledDeriver = new SubjectDeriver(config);
        disabledDeriver.init();

        // Should return "misc" for any path when disabled
        assertThat(disabledDeriver.deriveSubject("docs/security-oidc.adoc"))
                .isEqualTo("misc");
        assertThat(disabledDeriver.deriveSubject("docs/rest-client.adoc"))
                .isEqualTo("misc");
    }
}
