package com.fvd.api.services;

import com.fvd.api.dto.CodeBlockInfo;
import com.fvd.api.dto.DocumentResponse;
import com.fvd.api.dto.DocumentSearchResponse;
import com.fvd.api.dto.SectionInfo;
import com.fvd.common.utils.AsciiDocCleaner;
import com.fvd.common.utils.DocumentTitleExtractor;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.search.services.MatchedKeyword;
import com.fvd.search.services.FileSearchResult;
import com.fvd.search.services.PaginatedResult;
import com.fvd.search.services.SearchService;
import com.fvd.subject.services.SubjectDeriver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for document retrieval and search operations.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class DocumentService {

    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("^:description:\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern SECTION_HEADER = Pattern.compile("^(={2,5})\\s+(.+)$");

    private final DocStore docStore;
    private final DocParser docParser;
    private final KeywordIndexStore keywordIndexStore;
    private final SearchService searchService;
    private final SubjectDeriver subjectDeriver;

    /**
     * Retrieves a document by path with full structured content.
     *
     * @param version the documentation version
     * @param path the document path
     * @return the document response, or null if not found
     */
    public DocumentResponse getDocumentByPath(String version, String path) {
        Optional<String> contentOpt = docStore.read(version, path);
        if (contentOpt.isEmpty()) {
            return null;
        }

        String content = contentOpt.get();
        String extension = findExtensionForPath(version, path);
        String subject = subjectDeriver.deriveSubject(path);

        return buildDocumentResponse(path, content, extension, subject, List.of(), null);
    }

    /**
     * Searches documents by keywords.
     *
     * @param version the documentation version
     * @param keywords the search keywords
     * @param subject optional subject filter
     * @param extension optional extension filter
     * @param limit max results
     * @param offset pagination offset
     * @return search response with matching documents
     */
    public DocumentSearchResponse searchDocuments(String version, List<String> keywords,
                                                  String subject, String extension,
                                                  int limit, int offset) {
        // Use existing search service for keyword matching
        PaginatedResult<FileSearchResult> searchResult = searchService.searchFiles(
                version, keywords, extension, subject, limit, offset);

        List<DocumentResponse> results = new ArrayList<>();
        for (FileSearchResult fileResult : searchResult.items()) {
            String derivedSubject = subjectDeriver.deriveSubject(fileResult.path);

            // Get full document content
            Optional<String> contentOpt = docStore.read(version, fileResult.path);
            if (contentOpt.isEmpty()) {
                continue;
            }

            List<String> matchedKws = fileResult.matchedKeywords.stream()
                    .map(MatchedKeyword::originalKeyword)
                    .toList();

            DocumentResponse doc = buildDocumentResponse(
                    fileResult.path,
                    contentOpt.get(),
                    fileResult.extension,
                    derivedSubject,
                    matchedKws,
                    fileResult.score
            );
            results.add(doc);
        }

        return DocumentSearchResponse.builder()
                .results(results)
                .totalCount(searchResult.total())
                .returnedCount(results.size())
                .build();
    }

    private DocumentResponse buildDocumentResponse(String path, String content,
                                                   String extension, String subject,
                                                   List<String> matchedKeywords, Double score) {
        String title = DocumentTitleExtractor.extractTitle(content);
        String description = extractDescription(content);
        List<SectionInfo> sections = parseSections(content);
        List<CodeBlockInfo> codeBlocks = parseCodeBlocks(content);

        return new DocumentResponse(
                title,
                description,
                path,
                subject,
                extension,
                sections,
                codeBlocks,
                matchedKeywords,
                score
        );
    }

    private String extractDescription(String content) {
        Matcher matcher = DESCRIPTION_PATTERN.matcher(content);
        if (matcher.find()) {
            return AsciiDocCleaner.clean(matcher.group(1));
        }
        // Fall back to first paragraph after title
        String[] lines = content.split("\n");
        StringBuilder desc = new StringBuilder();
        boolean foundTitle = false;
        for (String line : lines) {
            if (line.startsWith("= ")) {
                foundTitle = true;
                continue;
            }
            if (foundTitle && !line.isBlank() && !line.startsWith(":") && !line.startsWith("=")) {
                if (!desc.isEmpty()) {
                    desc.append(" ");
                }
                desc.append(line.trim());
                if (desc.length() > 200) {
                    break;
                }
            }
            if (foundTitle && line.startsWith("==")) {
                break;
            }
        }
        return AsciiDocCleaner.clean(desc.toString());
    }

    private List<SectionInfo> parseSections(String content) {
        List<DocParser.Section> parsedSections = docParser.parseSections(content);
        String[] lines = content.split("\n", -1);

        List<SectionInfo> sections = new ArrayList<>();
        for (DocParser.Section section : parsedSections) {
            int level = determineSectionLevel(lines, section.startLine());
            int startIdx = Math.max(0, section.startLine() - 1);
            int endIdx = Math.min(lines.length, section.endLine());
            String sectionContent = String.join("\n", Arrays.copyOfRange(lines, startIdx, endIdx));

            sections.add(new SectionInfo(
                    section.title(),
                    level,
                    sectionContent,
                    section.startLine(),
                    section.endLine()
            ));
        }

        return sections;
    }

    private int determineSectionLevel(String[] lines, int startLine) {
        // Look backwards from startLine to find the section header
        for (int i = startLine - 1; i >= 0 && i < lines.length; i--) {
            Matcher matcher = SECTION_HEADER.matcher(lines[i].trim());
            if (matcher.matches()) {
                return matcher.group(1).length(); // Number of = signs indicates level
            }
        }
        return 2; // Default to level 2
    }

    private List<CodeBlockInfo> parseCodeBlocks(String content) {
        List<DocParser.CodeBlock> parsedBlocks = docParser.parseCodeBlocks(content);

        List<CodeBlockInfo> codeBlocks = new ArrayList<>();
        for (DocParser.CodeBlock block : parsedBlocks) {
            codeBlocks.add(new CodeBlockInfo(
                    block.language(),
                    block.content(),
                    block.sectionTitle(), // context is the section title
                    block.startLine(),
                    block.endLine()
            ));
        }

        return codeBlocks;
    }

    private String findExtensionForPath(String version, String path) {
        Optional<KeywordIndex> indexOpt = keywordIndexStore.read(version);
        if (indexOpt.isEmpty()) {
            return null;
        }

        for (FileKeywordEntry file : indexOpt.get().files) {
            if (file.path.equals(path)) {
                return file.extension;
            }
        }
        return null;
    }
}
