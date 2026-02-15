package com.fvd.api.services;

import com.fvd.api.dto.BatchDocumentError;
import com.fvd.api.dto.BatchDocumentResponse;
import com.fvd.api.dto.CodeBlockInfo;
import com.fvd.api.dto.DocumentResponse;
import com.fvd.api.dto.DocumentSearchResponse;
import com.fvd.api.dto.SectionInfo;
import com.fvd.common.utils.DescriptionExtractor;
import com.fvd.common.utils.DocumentTitleExtractor;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.search.services.MatchedKeyword;
import com.fvd.search.services.FileSearchResult;
import com.fvd.asciidocs.model.DocumentMetadata;
import com.fvd.search.services.PaginatedResult;
import com.fvd.search.services.SearchService;
import com.fvd.subject.services.MetadataAwareSubjectResolver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for document retrieval and search operations.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class DocumentService {

    private static final Pattern SECTION_HEADER = Pattern.compile("^(={2,5})\\s+(.+)$");

    /**
     * Maximum number of documents returned with full content (brief=false).
     * Loading full content (sections + codeBlocks) is expensive — each document
     * requires file I/O and AsciiDoc parsing.
     */
    private static final int FULL_CONTENT_MAX_LIMIT = 5;

    private final DocStore docStore;
    private final DocParser docParser;
    private final KeywordIndexStore keywordIndexStore;
    private final SearchService searchService;
    private final MetadataAwareSubjectResolver metadataResolver;

    private final Map<String, ParsedDocument> documentCache = new ConcurrentHashMap<>();

    @ConfigProperty(name = "app.document-cache.enabled", defaultValue = "true")
    boolean documentCacheEnabled;

    /**
     * Holds the parsed content of a document that is invariant across requests.
     * Search-specific fields (matchedKeywords, score) are not included.
     */
    record ParsedDocument(
            String title,
            String description,
            String path,
            String subject,
            String extension,
            List<SectionInfo> sections,
            List<CodeBlockInfo> codeBlocks
    ) {}

    /**
     * Retrieves a document by path with full structured content.
     *
     * @param version the documentation version
     * @param path the document path
     * @return the document response, or null if not found
     */
    public DocumentResponse getDocumentByPath(String version, String path) {
        ParsedDocument parsed = getOrParseDocument(version, path);
        if (parsed == null) {
            return null;
        }
        return new DocumentResponse(
                parsed.title(), parsed.description(), parsed.path(),
                parsed.subject(), parsed.extension(),
                parsed.sections(), parsed.codeBlocks(),
                List.of(), null);
    }

    /**
     * Retrieves multiple documents by path. Handles partial failures gracefully.
     *
     * @param version the documentation version
     * @param paths list of document paths to retrieve
     * @param brief when true, returns only title and description without sections/code blocks
     * @return batch response with documents and errors
     */
    public BatchDocumentResponse getDocumentsBatch(String version, List<String> paths, boolean brief) {
        List<DocumentResponse> documents = new ArrayList<>();
        List<BatchDocumentError> errors = new ArrayList<>();

        for (String path : paths) {
            try {
                DocumentResponse doc = brief
                        ? getDocumentByPathBrief(version, path)
                        : getDocumentByPath(version, path);
                if (doc != null) {
                    documents.add(doc);
                } else {
                    errors.add(new BatchDocumentError(path, "Document not found"));
                }
            } catch (Exception e) {
                log.warn("Error retrieving document '{}': {}", path, e.getMessage());
                errors.add(new BatchDocumentError(path, "Error reading document"));
            }
        }

        return new BatchDocumentResponse(documents, errors, paths.size(), documents.size(), errors.size());
    }

    /**
     * Retrieves a document by path with only title, description, subject, and extension.
     * Skips section and code block parsing for performance.
     */
    private DocumentResponse getDocumentByPathBrief(String version, String path) {
        Optional<String> contentOpt = docStore.read(version, path);
        if (contentOpt.isEmpty()) {
            return null;
        }

        String content = contentOpt.get();
        String title = DocumentTitleExtractor.extractTitle(content);
        String description = DescriptionExtractor.extract(content);
        String extension = findExtensionForPath(version, path);
        String subject = metadataResolver.resolveSubject(version, path);

        return new DocumentResponse(title, description, path, subject, extension,
                null, null, List.of(), null);
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
     * @param brief when true, returns only title and description without sections and code blocks
     * @return search response with matching documents
     */
    public DocumentSearchResponse searchDocuments(String version, List<String> keywords,
                                                  String subject, String extension,
                                                  int limit, int offset, boolean brief) {
        // Enforce lower limit for non-brief mode to prevent timeout
        int effectiveLimit = brief ? limit : Math.min(limit, FULL_CONTENT_MAX_LIMIT);

        // Use existing search service for keyword matching
        PaginatedResult<FileSearchResult> searchResult = searchService.searchFiles(
                version, keywords, extension, subject, effectiveLimit, offset);

        List<DocumentResponse> results = new ArrayList<>();
        Map<String, DocumentMetadata> metadataMap = metadataResolver.loadMetadataMap(version);
        for (FileSearchResult fileResult : searchResult.items()) {
            String derivedSubject = metadataResolver.resolveSubject(fileResult.path, metadataMap);

            List<String> matchedKws = fileResult.matchedKeywords.stream()
                    .map(MatchedKeyword::originalKeyword)
                    .toList();

            Optional<String> contentOpt = docStore.read(version, fileResult.path);
            if (contentOpt.isEmpty()) {
                continue;
            }

            if (brief) {
                String title = DocumentTitleExtractor.extractTitle(contentOpt.get());
                String description = DescriptionExtractor.extract(contentOpt.get());
                results.add(new DocumentResponse(
                        title, description, fileResult.path, derivedSubject,
                        fileResult.extension, null, null, matchedKws, fileResult.score));
            } else {
                ParsedDocument parsed = getOrParseDocument(version, fileResult.path);
                if (parsed == null) {
                    continue;
                }
                results.add(new DocumentResponse(
                        parsed.title(), parsed.description(), parsed.path(),
                        parsed.subject(), parsed.extension(),
                        parsed.sections(), parsed.codeBlocks(),
                        matchedKws, fileResult.score));
            }
        }

        DocumentSearchResponse.DocumentSearchResponseBuilder<?, ?> builder = DocumentSearchResponse.builder()
                .results(results)
                .totalCount(searchResult.total())
                .returnedCount(results.size());

        if (!brief && searchResult.total() > FULL_CONTENT_MAX_LIMIT) {
            builder.warning("Full content mode (brief=false) is limited to " + FULL_CONTENT_MAX_LIMIT +
                    " results for performance. Use brief=true (default) for larger result sets, " +
                    "then fetch individual documents by path.");
        }

        return builder.build();
    }

    /**
     * Invalidates the in-memory document parse cache for a specific version.
     * Should be called after documents are updated (e.g., during cache refresh).
     */
    public void invalidateDocumentCache(String version) {
        String prefix = version + "::";
        documentCache.keySet().removeIf(key -> key.startsWith(prefix));
        log.info("Document parse cache invalidated for version {}", version);
    }

    private ParsedDocument getOrParseDocument(String version, String path) {
        String cacheKey = version + "::" + path;

        if (documentCacheEnabled) {
            ParsedDocument cached = documentCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        Optional<String> contentOpt = docStore.read(version, path);
        if (contentOpt.isEmpty()) {
            return null;
        }

        String content = contentOpt.get();
        String extension = findExtensionForPath(version, path);
        String subject = metadataResolver.resolveSubject(version, path);
        String title = DocumentTitleExtractor.extractTitle(content);
        String description = DescriptionExtractor.extract(content);
        List<SectionInfo> sections = parseSections(content);
        List<CodeBlockInfo> codeBlocks = parseCodeBlocks(content);

        ParsedDocument parsed = new ParsedDocument(
                title, description, path, subject, extension, sections, codeBlocks);

        if (documentCacheEnabled) {
            documentCache.put(cacheKey, parsed);
        }

        return parsed;
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
