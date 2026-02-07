package com.fvd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AsciidocParser {

    private static final int MIN_TOKEN_LENGTH = 3;
    private static final Pattern SECTION_HEADER = Pattern.compile("^(={1,5})\\s+(.+)$");
    private static final Pattern CODE_BLOCK_DELIMITER = Pattern.compile("^-{4,}$");
    private static final Pattern NON_WORD = Pattern.compile("[^a-zA-Z0-9-]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public record Section(String title, int startLine, int endLine, Map<String, Integer> keywords) {
    }

    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(WHITESPACE.split(text.trim()))
                .map(word -> NON_WORD.matcher(word).replaceAll(""))
                .map(String::toLowerCase)
                .filter(w -> w.length() >= MIN_TOKEN_LENGTH)
                .toList();
    }

    public Map<String, Integer> extractKeywords(String text) {
        String cleaned = stripCodeBlocks(text);
        List<String> tokens = tokenize(cleaned);
        Map<String, Integer> counts = new HashMap<>();
        for (String token : tokens) {
            counts.merge(token, 1, Integer::sum);
        }
        return counts;
    }

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
}
