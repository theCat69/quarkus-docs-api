package com.fvd.subject.services;

import com.fvd.asciidocs.model.DocumentMetadata;
import com.fvd.indexs.stores.DocumentMetadataStore;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetadataAwareSubjectResolverTest {

    @Mock
    private SubjectDeriver subjectDeriver;

    @Mock
    private DocumentMetadataStore documentMetadataStore;

    @InjectMocks
    private MetadataAwareSubjectResolver resolver;

    @Nested
    class LazyResolveSubjectTests {

        @Test
        void resolveSubjectLoadsMetadataFromStoreAndDelegatesToDeriver() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("security"))
                    .topics(List.of())
                    .build();

            when(documentMetadataStore.readByPath("main", "security-oidc.adoc"))
                    .thenReturn(Optional.of(metadata));
            when(subjectDeriver.deriveSubject("security-oidc.adoc", metadata))
                    .thenReturn("security");

            String result = resolver.resolveSubject("main", "security-oidc.adoc");

            assertThat(result).isEqualTo("security");
            verify(documentMetadataStore).readByPath("main", "security-oidc.adoc");
            verify(subjectDeriver).deriveSubject("security-oidc.adoc", metadata);
        }

        @Test
        void resolveSubjectPassesNullMetadataWhenNotFoundInStore() {
            when(documentMetadataStore.readByPath("3.17", "unknown-doc.adoc"))
                    .thenReturn(Optional.empty());
            when(subjectDeriver.deriveSubject("unknown-doc.adoc", null))
                    .thenReturn("misc");

            String result = resolver.resolveSubject("3.17", "unknown-doc.adoc");

            assertThat(result).isEqualTo("misc");
            verify(documentMetadataStore).readByPath("3.17", "unknown-doc.adoc");
            verify(subjectDeriver).deriveSubject("unknown-doc.adoc", null);
        }

        @Test
        void resolveSubjectUsesCorrectVersion() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("data"))
                    .topics(List.of())
                    .build();

            when(documentMetadataStore.readByPath("3.15", "hibernate-orm.adoc"))
                    .thenReturn(Optional.of(metadata));
            when(subjectDeriver.deriveSubject("hibernate-orm.adoc", metadata))
                    .thenReturn("data-persistence");

            String result = resolver.resolveSubject("3.15", "hibernate-orm.adoc");

            assertThat(result).isEqualTo("data-persistence");
            verify(documentMetadataStore).readByPath("3.15", "hibernate-orm.adoc");
        }
    }

    @Nested
    class BatchResolveSubjectTests {

        @Test
        void resolveSubjectFromMapDelegatesToDeriverWithMetadata() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .categories(List.of("web"))
                    .topics(List.of())
                    .build();

            Map<String, DocumentMetadata> metadataMap = Map.of(
                    "rest-guide.adoc", metadata
            );

            when(subjectDeriver.deriveSubject("rest-guide.adoc", metadata))
                    .thenReturn("rest-apis");

            String result = resolver.resolveSubject("rest-guide.adoc", metadataMap);

            assertThat(result).isEqualTo("rest-apis");
            verify(subjectDeriver).deriveSubject("rest-guide.adoc", metadata);
        }

        @Test
        void resolveSubjectFromMapPassesNullWhenPathNotInMap() {
            Map<String, DocumentMetadata> metadataMap = Map.of(
                    "other-doc.adoc", DocumentMetadata.empty()
            );

            when(subjectDeriver.deriveSubject("missing-doc.adoc", null))
                    .thenReturn("misc");

            String result = resolver.resolveSubject("missing-doc.adoc", metadataMap);

            assertThat(result).isEqualTo("misc");
            verify(subjectDeriver).deriveSubject("missing-doc.adoc", null);
        }

        @Test
        void resolveSubjectFromEmptyMapPassesNullMetadata() {
            Map<String, DocumentMetadata> emptyMap = Map.of();

            when(subjectDeriver.deriveSubject("some-doc.adoc", null))
                    .thenReturn("misc");

            String result = resolver.resolveSubject("some-doc.adoc", emptyMap);

            assertThat(result).isEqualTo("misc");
            verify(subjectDeriver).deriveSubject("some-doc.adoc", null);
        }
    }

    @Nested
    class LoadMetadataMapTests {

        @Test
        void loadMetadataMapDelegatesToStore() {
            DocumentMetadata meta1 = DocumentMetadata.builder()
                    .categories(List.of("security"))
                    .topics(List.of())
                    .build();
            DocumentMetadata meta2 = DocumentMetadata.builder()
                    .categories(List.of("web"))
                    .topics(List.of())
                    .build();

            Map<String, DocumentMetadata> expectedMap = Map.of(
                    "security-oidc.adoc", meta1,
                    "rest-guide.adoc", meta2
            );

            when(documentMetadataStore.readAll("main")).thenReturn(expectedMap);

            Map<String, DocumentMetadata> result = resolver.loadMetadataMap("main");

            assertThat(result).isEqualTo(expectedMap);
            assertThat(result).hasSize(2);
            verify(documentMetadataStore).readAll("main");
        }

        @Test
        void loadMetadataMapReturnsEmptyMapWhenNoMetadata() {
            when(documentMetadataStore.readAll("3.17")).thenReturn(Map.of());

            Map<String, DocumentMetadata> result = resolver.loadMetadataMap("3.17");

            assertThat(result).isEmpty();
            verify(documentMetadataStore).readAll("3.17");
        }
    }
}
