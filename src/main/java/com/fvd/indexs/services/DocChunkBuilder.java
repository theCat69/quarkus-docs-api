package com.fvd.indexs.services;

import com.fvd.asciidocs.model.DocumentMetadata;
import com.fvd.common.utils.UrlBuilder;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.model.DocChunk;
import com.fvd.indexs.stores.DocChunkStore;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds DocChunk records from parsed document sections and persists them
 * to the DocChunkStore for full-text search indexing.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class DocChunkBuilder {

    private static final String ADOC_SUFFIX = ".adoc";
    private static final String DEFAULT_EXTENSION = "quarkus-core";
    private static final int MAX_SUMMARY_LENGTH = 200;

    private static final Pattern TITLE_PATTERN = Pattern.compile("^= (.+)$", Pattern.MULTILINE);
    private static final Pattern HEADING_PREFIX = Pattern.compile("^={1,5}\\s+");
    private static final Pattern FIRST_SENTENCE = Pattern.compile("^(.*?\\.)(?:\\s|$)");
    private static final Pattern APOSTROPHES = Pattern.compile("['`]");
    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9-]");
    private static final Pattern MULTIPLE_DASHES = Pattern.compile("-{2,}");

    private final DocParser docParser;
    private final DocChunkStore docChunkStore;
    private final UrlBuilder urlBuilder;
    private final DocStore docStore;

    /**
     * Processes core docs with extension "quarkus-core".
     */
    public void build(String version, List<String> filePaths) {
        build(version, filePaths, DEFAULT_EXTENSION);
    }

    /**
     * Processes extension docs with a specific extension identifier.
     */
    public void build(String version, List<String> filePaths, String extension) {
        log.info("Building doc chunks for version '{}' with {} files (extension: {})",
                version, filePaths.size(), extension);

        List<DocChunk> allChunks = processFiles(version, filePaths, extension);

        docChunkStore.deleteByVersion(version);
        docChunkStore.insertBatch(version, allChunks);

        log.info("Finished building doc chunks for version '{}': {} chunks from {} files",
                version, allChunks.size(), filePaths.size());
    }

    /**
     * Iterates a map of extension to file paths, building chunks for each entry.
     */
    public void build(String version, Map<String, List<String>> extensionFiles) {
        log.info("Building doc chunks for version '{}' with {} extensions",
                version, extensionFiles.size());

        List<DocChunk> allChunks = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : extensionFiles.entrySet()) {
            String extension = entry.getKey();
            List<String> files = entry.getValue();
            allChunks.addAll(processFiles(version, files, extension));
        }

        docChunkStore.deleteByVersion(version);
        docChunkStore.insertBatch(version, allChunks);

        log.info("Finished building doc chunks for version '{}': {} chunks total",
                version, allChunks.size());
    }

    private List<DocChunk> processFiles(String version, List<String> filePaths, String extension) {
        List<DocChunk> chunks = new ArrayList<>();

        for (String filePath : filePaths) {
            Optional<String> contentOpt = docStore.read(version, filePath);
            if (contentOpt.isEmpty()) {
                log.warn("Could not read file '{}' for version '{}', skipping", filePath, version);
                continue;
            }

            String content = contentOpt.get();
            String page = stripAdocSuffix(filePath);
            List<DocParser.Section> sections = docParser.parseSections(content);
            DocumentMetadata metadata = docParser.extractMetadata(content);
            String documentTitle = extractDocumentTitle(content, page);
            List<String> topics = buildTopics(metadata);
            List<String> extensions = buildExtensions(extension, metadata);

            for (int i = 0; i < sections.size(); i++) {
                DocParser.Section section = sections.get(i);
                String sectionContent = extractSectionContent(content, section, sections, i);
                String summary = extractFirstSentence(sectionContent);
                String id = page + "#" + slugify(section.title());

                DocChunk chunk = new DocChunk(
                        id,
                        version,
                        page,
                        documentTitle,
                        section.title(),
                        urlBuilder.buildUrl(page, section.title()),
                        topics,
                        extensions,
                        summary,
                        sectionContent
                );
                chunks.add(chunk);
            }
        }

        return chunks;
    }

    private String stripAdocSuffix(String filePath) {
        if (filePath != null && filePath.endsWith(ADOC_SUFFIX)) {
            return filePath.substring(0, filePath.length() - ADOC_SUFFIX.length());
        }
        return filePath;
    }

    private String extractDocumentTitle(String content, String fallbackPage) {
        Matcher matcher = TITLE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return fallbackPage;
    }

    private List<String> buildTopics(DocumentMetadata metadata) {
        LinkedHashSet<String> topics = new LinkedHashSet<>();
        if (metadata.getTopics() != null) {
            topics.addAll(metadata.getTopics());
        }
        if (metadata.getCategories() != null) {
            topics.addAll(metadata.getCategories());
        }
        return List.copyOf(topics);
    }

    private List<String> buildExtensions(String extension, DocumentMetadata metadata) {
        if (extension != null) {
            return List.of(extension);
        }
        if (metadata.getExtensions() != null && !metadata.getExtensions().isEmpty()) {
            return metadata.getExtensions();
        }
        return List.of(DEFAULT_EXTENSION);
    }

    String extractSectionContent(String fullContent, DocParser.Section section,
                                 List<DocParser.Section> allSections, int sectionIndex) {
        String[] lines = fullContent.split("\n", -1);
        int startLine = section.startLine();
        int endLine;

        if (sectionIndex + 1 < allSections.size()) {
            endLine = allSections.get(sectionIndex + 1).startLine() - 1;
        } else {
            endLine = lines.length - 1;
        }

        startLine = Math.max(0, Math.min(startLine, lines.length - 1));
        endLine = Math.max(startLine, Math.min(endLine, lines.length - 1));

        StringBuilder sb = new StringBuilder();
        for (int i = startLine; i <= endLine; i++) {
            if (i > startLine) {
                sb.append("\n");
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    String extractFirstSentence(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String cleaned = HEADING_PREFIX.matcher(text).replaceAll("");
        cleaned = cleaned.strip();

        if (cleaned.isEmpty()) {
            return "";
        }

        Matcher matcher = FIRST_SENTENCE.matcher(cleaned);
        if (matcher.find()) {
            String sentence = matcher.group(1).trim();
            if (sentence.length() > MAX_SUMMARY_LENGTH) {
                return sentence.substring(0, MAX_SUMMARY_LENGTH);
            }
            return sentence;
        }

        if (cleaned.length() > MAX_SUMMARY_LENGTH) {
            return cleaned.substring(0, MAX_SUMMARY_LENGTH);
        }
        return cleaned;
    }

    String slugify(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String lower = text.toLowerCase();
        String noApostrophes = APOSTROPHES.matcher(lower).replaceAll("");
        String replaced = NON_SLUG_CHARS.matcher(noApostrophes).replaceAll("-");
        String collapsed = MULTIPLE_DASHES.matcher(replaced).replaceAll("-");
        return collapsed.replaceAll("^-+|-+$", "");
    }
}
