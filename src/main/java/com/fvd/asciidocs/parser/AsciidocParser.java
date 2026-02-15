package com.fvd.asciidocs.parser;

import com.fvd.asciidocs.model.DocumentMetadata;
import com.fvd.common.Stemmer;
import com.fvd.common.utils.AsciiDocCleaner;
import com.fvd.docs.parser.DocParser;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.search.SearchConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
@RequiredArgsConstructor
public class AsciidocParser implements DocParser {

    private static final String FILE_SUFFIX = ".adoc";
    private static final Pattern SECTION_HEADER = Pattern.compile("^(={1,5})\\s+(.+)$");
    private static final Pattern CODE_BLOCK_DELIMITER = Pattern.compile("^-{4,}$");
    private static final Pattern SOURCE_ATTRIBUTE = Pattern.compile("^\\[source(?:,\\s*([^\\]]+))?\\]$");
    private static final Pattern NON_WORD = Pattern.compile("[^a-zA-Z0-9-]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern HEADER_ATTRIBUTE = Pattern.compile(
            "^:([a-zA-Z][a-zA-Z0-9_-]*):\\s*(.*)$", Pattern.MULTILINE);

    private final SearchConfig searchConfig;

    @Override
    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int minTokenLength = searchConfig.index().minTokenLength();
        return Arrays.stream(WHITESPACE.split(text.trim()))
                .map(word -> NON_WORD.matcher(word).replaceAll(""))
                .map(String::toLowerCase)
                .filter(w -> w.length() >= minTokenLength)
                .toList();
    }

    @Override
    public Map<String, Integer> extractKeywords(String text) {
        String cleaned = stripCodeBlocks(text);
        cleaned = AsciiDocCleaner.clean(cleaned);
        List<String> tokens = tokenize(cleaned);
        Map<String, Integer> counts = new HashMap<>();
        for (String token : tokens) {
            if(!KeywordIndexer.WORD_INDEX_BLACK_LIST.contains(token)) {
                counts.merge(Stemmer.stem(token), 1, Integer::sum);
            }
        }
        return counts;
    }

    @Override
    public List<Section> parseSections(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] lines = text.split("\n", -1);
        List<Section> sections = new ArrayList<>();
        String currentTitle = "";
        int currentStart = 1;
        List<String> currentLines = new ArrayList<>();
        boolean inCodeBlock = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (CODE_BLOCK_DELIMITER.matcher(line.trim()).matches()) {
                inCodeBlock = !inCodeBlock;
                currentLines.add(line);
                continue;
            }

            if (inCodeBlock) {
                currentLines.add(line);
                continue;
            }

            var matcher = SECTION_HEADER.matcher(line.trim());
            if (matcher.matches()) {
                // Flush previous section if it has content
                if (hasNonBlankContent(currentLines) || (currentStart == 1 && i > 0)) {
                    int endLine = i; // 0-based exclusive, but we want 1-based inclusive of last non-empty
                    int actualEnd = computeEndLine(currentStart, i);
                    if (actualEnd >= currentStart) {
                        String sectionText = String.join("\n", currentLines);
                        sections.add(new Section(currentTitle, currentStart, actualEnd, extractKeywords(sectionText)));
                    }
                }
                currentTitle = matcher.group(2).trim();
                currentStart = i + 1; // 1-based
                currentLines = new ArrayList<>();
            } else {
                currentLines.add(line);
            }
        }

        // Flush last section if it has non-blank content
        if (hasNonBlankContent(currentLines)) {
            int actualEnd = computeEndLine(currentStart, lines.length);
            if (actualEnd >= currentStart) {
                String sectionText = String.join("\n", currentLines);
                sections.add(new Section(currentTitle, currentStart, actualEnd, extractKeywords(sectionText)));
            }
        }

        return sections;
    }

    private int computeEndLine(int startLine, int nextSectionIndex) {
        // endLine is 1-based, representing the last line of this section
        // nextSectionIndex is 0-based index of the next section header (or lines.length)
        return nextSectionIndex; // 0-based exclusive becomes 1-based inclusive of the last line
    }

    private boolean hasNonBlankContent(List<String> lines) {
        return lines.stream().anyMatch(line -> !line.isBlank());
    }

    String stripCodeBlocks(String text) {
        StringBuilder result = new StringBuilder();
        boolean inCodeBlock = false;
        for (String line : text.split("\n", -1)) {
            if (CODE_BLOCK_DELIMITER.matcher(line.trim()).matches()) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (!inCodeBlock) {
                result.append(line).append("\n");
            }
        }
        return result.toString();
    }

    @Override
    public List<CodeBlock> parseCodeBlocks(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] lines = text.split("\n", -1);
        List<CodeBlock> codeBlocks = new ArrayList<>();
        String currentSection = "";
        String pendingLanguage = null;
        boolean inCodeBlock = false;
        int codeBlockStart = -1;
        List<String> codeLines = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();

            // Track section headers (outside code blocks)
            if (!inCodeBlock) {
                Matcher sectionMatcher = SECTION_HEADER.matcher(trimmed);
                if (sectionMatcher.matches()) {
                    currentSection = sectionMatcher.group(2).trim();
                    continue;
                }

                // Detect [source,language] attribute
                Matcher sourceMatcher = SOURCE_ATTRIBUTE.matcher(trimmed);
                if (sourceMatcher.matches()) {
                    pendingLanguage = sourceMatcher.group(1);
                    if (pendingLanguage != null) {
                        pendingLanguage = pendingLanguage.trim().toLowerCase();
                    }
                    continue;
                }
            }

            // Toggle code block on delimiter
            if (CODE_BLOCK_DELIMITER.matcher(trimmed).matches()) {
                if (!inCodeBlock) {
                    inCodeBlock = true;
                    codeBlockStart = i + 1; // 1-based
                    codeLines = new ArrayList<>();
                } else {
                    // End of code block - flush
                    int codeBlockEnd = i + 1; // 1-based, inclusive of the delimiter line
                    String content = String.join("\n", codeLines);
                    String language = pendingLanguage != null ? pendingLanguage : "";
                    codeBlocks.add(new CodeBlock(language, content, currentSection, codeBlockStart, codeBlockEnd));
                    inCodeBlock = false;
                    pendingLanguage = null;
                }
                continue;
            }

            if (inCodeBlock) {
                codeLines.add(lines[i]);
            } else {
                // Reset pending language if we see a non-source, non-blank line outside code blocks
                if (!trimmed.isEmpty() && pendingLanguage != null) {
                    pendingLanguage = null;
                }
            }
        }

        return codeBlocks;
    }

    @Override
    public String docsPrefix(String version) {
        return "_versions/" + version + "/guides/";
    }

    @Override
    @Deprecated
    public String docsPrefix() {
        return docsPrefix("main");
    }

    @Override
    public String fileSuffix() {
        return FILE_SUFFIX;
    }

    /**
     * Extracts document metadata attributes from the AsciiDoc header block.
     * Parses :categories:, :topics:, :extensions:, :summary:, and :diataxis-type:.
     *
     * @param content the full AsciiDoc content
     * @return extracted metadata (never null; fields may be empty lists or null)
     */
    @Override
    public DocumentMetadata extractMetadata(String content) {
        if (content == null || content.isBlank()) {
            return DocumentMetadata.empty();
        }
        String headerBlock = extractHeaderBlock(content);
        Map<String, String> attributes = new HashMap<>();
        Matcher matcher = HEADER_ATTRIBUTE.matcher(headerBlock);
        while (matcher.find()) {
            attributes.put(matcher.group(1), matcher.group(2).trim());
        }
        return DocumentMetadata.fromAttributes(attributes);
    }

    /**
     * Extracts the header block from AsciiDoc content.
     * The header block starts at the beginning of the file and ends at the first
     * level-2 section header (== ).
     */
    String extractHeaderBlock(String content) {
        StringBuilder header = new StringBuilder();
        for (String line : content.split("\n")) {
            if (line.startsWith("== ")) {
                break;
            }
            header.append(line).append("\n");
        }
        return header.toString();
    }
}
