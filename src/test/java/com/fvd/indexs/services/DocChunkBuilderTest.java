package com.fvd.indexs.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fvd.asciidocs.model.DocumentMetadata;
import com.fvd.common.utils.UrlBuilder;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.model.DocChunk;
import com.fvd.indexs.stores.DocChunkStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocChunkBuilderTest {

    @Mock
    private DocParser docParser;

    @Mock
    private DocChunkStore docChunkStore;

    @Mock
    private UrlBuilder urlBuilder;

    @Mock
    private DocStore docStore;

    @Captor
    private ArgumentCaptor<List<DocChunk>> chunksCaptor;

    private DocChunkBuilder docChunkBuilder;

    private static final String VERSION = "3.27";
    private static final String SAMPLE_CONTENT = """
            = Security Guide
            :categories: security, web
            :topics: authentication, authorization

            == Overview
            This is the overview section about security basics.
            It covers authentication and authorization.

            == Configuration
            Configuration details for security settings.
            Set up your application properties here.
            """;

    @BeforeEach
    void setUp() {
        docChunkBuilder = new DocChunkBuilder(docParser, docChunkStore, urlBuilder, docStore);
    }

    @Test
    void shouldBuildChunksWithDefaultExtensionForCoreDocs() {
        when(docStore.read(VERSION, "security.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of(
                new DocParser.Section("Overview", 4, 6, Map.of()),
                new DocParser.Section("Configuration", 8, 10, Map.of())
        ));
        when(docParser.extractMetadata(SAMPLE_CONTENT)).thenReturn(DocumentMetadata.empty());
        when(urlBuilder.buildUrl(eq("security"), any())).thenReturn("https://quarkus.io/guides/security#overview");

        docChunkBuilder.build(VERSION, List.of("security.adoc"));

        verify(docChunkStore).replaceVersion(eq(VERSION), chunksCaptor.capture());

        List<DocChunk> chunks = chunksCaptor.getValue();
        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.version()).isEqualTo(VERSION);
            assertThat(chunk.extensions()).containsExactly("quarkus-core");
        });
    }

    @Test
    void shouldBuildChunksWithSpecificExtension() {
        when(docStore.read(VERSION, "mailer.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of(
                new DocParser.Section("Overview", 4, 6, Map.of())
        ));
        when(docParser.extractMetadata(SAMPLE_CONTENT)).thenReturn(DocumentMetadata.empty());
        when(urlBuilder.buildUrl(eq("mailer"), any())).thenReturn("https://quarkus.io/guides/mailer#overview");

        docChunkBuilder.build(VERSION, List.of("mailer.adoc"), "quarkus-mailer");

        verify(docChunkStore).replaceVersion(eq(VERSION), chunksCaptor.capture());

        List<DocChunk> chunks = chunksCaptor.getValue();
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().extensions()).containsExactly("quarkus-mailer");
    }

    @Test
    void shouldBuildChunksFromMultipleExtensionsMap() {
        String mailerContent = "= Mailer Guide\n\n== Sending Emails\nHow to send emails.";
        String oidcContent = "= OIDC Guide\n\n== Token Validation\nHow to validate tokens.";

        when(docStore.read(VERSION, "mailer.adoc")).thenReturn(Optional.of(mailerContent));
        when(docStore.read(VERSION, "oidc.adoc")).thenReturn(Optional.of(oidcContent));
        when(docParser.parseSections(mailerContent)).thenReturn(List.of(
                new DocParser.Section("Sending Emails", 2, 3, Map.of())
        ));
        when(docParser.parseSections(oidcContent)).thenReturn(List.of(
                new DocParser.Section("Token Validation", 2, 3, Map.of())
        ));
        when(docParser.extractMetadata(any())).thenReturn(DocumentMetadata.empty());
        when(urlBuilder.buildUrl(any(), any())).thenReturn("https://quarkus.io/guides/test");

        Map<String, List<String>> extensionFiles = Map.of(
                "quarkus-mailer", List.of("mailer.adoc"),
                "quarkus-oidc", List.of("oidc.adoc")
        );

        docChunkBuilder.build(VERSION, extensionFiles);

        verify(docChunkStore).replaceVersion(eq(VERSION), chunksCaptor.capture());

        List<DocChunk> chunks = chunksCaptor.getValue();
        assertThat(chunks).hasSize(2);
    }

    @Test
    void shouldProduceDeterministicChunkIds() {
        when(docStore.read(VERSION, "security.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of(
                new DocParser.Section("Overview", 4, 6, Map.of())
        ));
        when(docParser.extractMetadata(SAMPLE_CONTENT)).thenReturn(DocumentMetadata.empty());
        when(urlBuilder.buildUrl(eq("security"), any())).thenReturn("https://quarkus.io/guides/security#overview");
        when(urlBuilder.toSlug("Overview")).thenReturn("overview");

        // Build twice — IDs should be the same
        docChunkBuilder.build(VERSION, List.of("security.adoc"));
        docChunkBuilder.build(VERSION, List.of("security.adoc"));

        verify(docChunkStore, org.mockito.Mockito.times(2)).replaceVersion(eq(VERSION), chunksCaptor.capture());
        List<List<DocChunk>> allCaptures = chunksCaptor.getAllValues();
        String firstId = allCaptures.get(0).getFirst().id();
        String secondId = allCaptures.get(1).getFirst().id();

        assertThat(firstId).isEqualTo(secondId);
        assertThat(firstId).isEqualTo("security#overview");
    }

    @Test
    void shouldExtractMetadataCorrectly() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .categories(List.of("security", "web"))
                .topics(List.of("authentication"))
                .extensions(List.of())
                .build();

        when(docStore.read(VERSION, "security.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of(
                new DocParser.Section("Overview", 4, 6, Map.of())
        ));
        when(docParser.extractMetadata(SAMPLE_CONTENT)).thenReturn(metadata);
        when(urlBuilder.buildUrl(eq("security"), any())).thenReturn("https://quarkus.io/guides/security#overview");

        docChunkBuilder.build(VERSION, List.of("security.adoc"));

        verify(docChunkStore).replaceVersion(eq(VERSION), chunksCaptor.capture());
        DocChunk chunk = chunksCaptor.getValue().getFirst();
        assertThat(chunk.title()).isEqualTo("Security Guide");
        assertThat(chunk.page()).isEqualTo("security");
        assertThat(chunk.topics()).containsExactly("authentication", "security", "web");
    }

    @Test
    void shouldSetVersionOnAllChunks() {
        when(docStore.read(VERSION, "security.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of(
                new DocParser.Section("Overview", 4, 6, Map.of()),
                new DocParser.Section("Configuration", 8, 10, Map.of())
        ));
        when(docParser.extractMetadata(SAMPLE_CONTENT)).thenReturn(DocumentMetadata.empty());
        when(urlBuilder.buildUrl(any(), any())).thenReturn("https://quarkus.io/guides/security");

        docChunkBuilder.build(VERSION, List.of("security.adoc"));

        verify(docChunkStore).replaceVersion(eq(VERSION), chunksCaptor.capture());
        assertThat(chunksCaptor.getValue()).allSatisfy(chunk ->
                assertThat(chunk.version()).isEqualTo(VERSION)
        );
    }

    @Test
    void shouldCallReplaceVersionAtomically() {
        when(docStore.read(VERSION, "security.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of(
                new DocParser.Section("Overview", 4, 6, Map.of())
        ));
        when(docParser.extractMetadata(SAMPLE_CONTENT)).thenReturn(DocumentMetadata.empty());
        when(urlBuilder.buildUrl(any(), any())).thenReturn("https://quarkus.io/guides/security#overview");

        docChunkBuilder.build(VERSION, List.of("security.adoc"));

        verify(docChunkStore).replaceVersion(eq(VERSION), any());
    }

    @Test
    void shouldSkipFilesThatCannotBeRead() {
        when(docStore.read(VERSION, "missing.adoc")).thenReturn(Optional.empty());
        when(docStore.read(VERSION, "security.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of(
                new DocParser.Section("Overview", 4, 6, Map.of())
        ));
        when(docParser.extractMetadata(SAMPLE_CONTENT)).thenReturn(DocumentMetadata.empty());
        when(urlBuilder.buildUrl(any(), any())).thenReturn("https://quarkus.io/guides/security#overview");

        docChunkBuilder.build(VERSION, List.of("missing.adoc", "security.adoc"));

        verify(docChunkStore).replaceVersion(eq(VERSION), chunksCaptor.capture());
        assertThat(chunksCaptor.getValue()).hasSize(1);
        assertThat(chunksCaptor.getValue().getFirst().page()).isEqualTo("security");
    }

    @Test
    void shouldProduceCorrectChunkCountFromSections() {
        when(docStore.read(VERSION, "security.adoc")).thenReturn(Optional.of(SAMPLE_CONTENT));
        when(docParser.parseSections(SAMPLE_CONTENT)).thenReturn(List.of(
                new DocParser.Section("Overview", 4, 6, Map.of()),
                new DocParser.Section("Configuration", 8, 10, Map.of()),
                new DocParser.Section("Advanced", 12, 14, Map.of())
        ));
        when(docParser.extractMetadata(SAMPLE_CONTENT)).thenReturn(DocumentMetadata.empty());
        when(urlBuilder.buildUrl(any(), any())).thenReturn("https://quarkus.io/guides/security");

        docChunkBuilder.build(VERSION, List.of("security.adoc"));

        verify(docChunkStore).replaceVersion(eq(VERSION), chunksCaptor.capture());
        assertThat(chunksCaptor.getValue()).hasSize(3);
    }

    @Test
    void extractSectionContentShouldExtractCorrectLineRange() {
        String content = "line0\nline1\nline2\nline3\nline4\nline5";
        List<DocParser.Section> sections = List.of(
                new DocParser.Section("First", 1, 2, Map.of()),
                new DocParser.Section("Second", 3, 5, Map.of())
        );

        String result = docChunkBuilder.extractSectionContent(content, sections.get(0), sections, 0);
        assertThat(result).isEqualTo("line1\nline2");
    }

    @Test
    void extractFirstSentenceShouldExtractFirstSentence() {
        String text = "This is the first sentence. This is the second sentence.";
        String result = docChunkBuilder.extractFirstSentence(text);
        assertThat(result).isEqualTo("This is the first sentence.");
    }

    @Test
    void extractFirstSentenceShouldReturnEmptyForBlankInput() {
        assertThat(docChunkBuilder.extractFirstSentence("")).isEqualTo("");
        assertThat(docChunkBuilder.extractFirstSentence(null)).isEqualTo("");
    }
}
