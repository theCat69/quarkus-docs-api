package com.fvd.indexs.indexers;

import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CodeSampleIndexer {

    private static final int IMPORT_BOOST = 5;
    private static final int FILENAME_BOOST = 10;
    private static final int SECTION_TITLE_BOOST = 5;
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([a-zA-Z][a-zA-Z0-9_.]+)\\s*;");

    private final DocStore docStore;
    private final CodeSampleIndexStore codeSampleIndexStore;
    private final DocParser parser;

    public CodeSampleIndex build(String version, List<String> filePaths) {
        List<CodeSampleEntry> entries = new ArrayList<>();

        for (String filePath : filePaths) {
            Optional<String> content = docStore.read(version, filePath);
            if (content.isEmpty()) {
                continue;
            }
            List<CodeSampleEntry> fileEntries = buildEntriesForFile(filePath, content.get());
            entries.addAll(fileEntries);
        }

        CodeSampleIndex index = new CodeSampleIndex(entries);
        codeSampleIndexStore.write(version, index);
        return index;
    }

    List<CodeSampleEntry> buildEntriesForFile(String filePath, String content) {
        List<DocParser.CodeBlock> codeBlocks = parser.parseCodeBlocks(content);
        List<DocParser.Section> sections = parser.parseSections(content);
        List<CodeSampleEntry> entries = new ArrayList<>();

        for (DocParser.CodeBlock block : codeBlocks) {
            Map<String, Integer> keywords = new HashMap<>();

            // Add keywords from the code content itself
            List<String> codeTokens = parser.tokenize(block.content());
            for (String token : codeTokens) {
                if (!KeywordIndexer.WORD_INDEX_BLACK_LIST.contains(token)) {
                    keywords.merge(token, 1, Integer::sum);
                }
            }

            // Add section keywords from the containing section
            addSectionKeywords(block, sections, keywords);

            // Boost import statements
            applyImportBoost(block.content(), keywords);

            // Boost filename and section title keywords
            applyFilenameBoost(filePath, keywords);
            applySectionTitleBoost(block.sectionTitle(), keywords);

            List<KeywordScore> scores = toSortedScores(keywords);

            entries.add(new CodeSampleEntry(
                    filePath, block.sectionTitle(), block.language(),
                    block.content(), block.startLine(), block.endLine(), scores));
        }

        return entries;
    }

    private void addSectionKeywords(DocParser.CodeBlock block, List<DocParser.Section> sections,
                                    Map<String, Integer> keywords) {
        for (DocParser.Section section : sections) {
            if (section.title().equals(block.sectionTitle())) {
                for (Map.Entry<String, Integer> entry : section.keywords().entrySet()) {
                    keywords.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
                break;
            }
        }
    }

    void applyImportBoost(String codeContent, Map<String, Integer> keywords) {
        for (String line : codeContent.split("\n")) {
            Matcher matcher = IMPORT_PATTERN.matcher(line);
            if (matcher.matches()) {
                String fqcn = matcher.group(1);
                // Tokenize the fully qualified class name (split on dots)
                String[] parts = fqcn.split("\\.");
                for (String part : parts) {
                    String token = part.toLowerCase();
                    if (token.length() >= 3 && !KeywordIndexer.WORD_INDEX_BLACK_LIST.contains(token)) {
                        keywords.merge(token, IMPORT_BOOST, Integer::sum);
                    }
                }
            }
        }
    }

    void applyFilenameBoost(String filePath, Map<String, Integer> keywords) {
        String filename = filePath;
        int lastSlash = filePath.lastIndexOf('/');
        if (lastSlash >= 0) {
            filename = filePath.substring(lastSlash + 1);
        }
        if (filename.endsWith(parser.fileSuffix())) {
            filename = filename.substring(0, filename.length() - parser.fileSuffix().length());
        }
        List<String> filenameTokens = parser.tokenize(filename.replace("-", " ").replace("_", " "));
        for (String token : filenameTokens) {
            keywords.merge(token, FILENAME_BOOST, Integer::sum);
        }
    }

    void applySectionTitleBoost(String sectionTitle, Map<String, Integer> keywords) {
        if (sectionTitle == null || sectionTitle.isBlank()) {
            return;
        }
        List<String> titleTokens = parser.tokenize(sectionTitle);
        for (String token : titleTokens) {
            keywords.merge(token, SECTION_TITLE_BOOST, Integer::sum);
        }
    }

    private List<KeywordScore> toSortedScores(Map<String, Integer> keywords) {
        return keywords.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(e -> new KeywordScore(e.getKey(), e.getValue()))
                .toList();
    }
}
