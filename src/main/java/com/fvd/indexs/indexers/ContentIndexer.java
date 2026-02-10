package com.fvd.indexs.indexers;

import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.stores.ContentIndexStore;
import com.fvd.search.SearchConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ContentIndexer {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9-]+");

    private final DocStore docStore;
    private final ContentIndexStore contentIndexStore;
    private final SearchConfig searchConfig;

    public ContentIndex build(String version, List<String> filePaths) {
        return build(version, filePaths, "quarkus-core");
    }

    public ContentIndex build(String version, List<String> filePaths, String extension) {
        Map<String, List<ContentOccurrence>> wordOccurrences = new HashMap<>();
        int minTokenLength = searchConfig.index().minTokenLength();

        for (String filePath : filePaths) {
            Optional<String> content = docStore.read(version, filePath);
            if (content.isEmpty()) {
                continue;
            }
            tokenizeAndIndex(filePath, content.get(), wordOccurrences, minTokenLength, extension);
        }

        ContentIndex index = new ContentIndex(wordOccurrences);
        contentIndexStore.write(version, index);
        log.info("Content index built for version {}: {} unique words across {} files",
                version, wordOccurrences.size(), filePaths.size());
        return index;
    }

    public ContentIndex build(String version, Map<String, List<String>> filePathsByExtension) {
        Map<String, List<ContentOccurrence>> wordOccurrences = new HashMap<>();
        int minTokenLength = searchConfig.index().minTokenLength();
        int totalFiles = 0;

        for (Map.Entry<String, List<String>> entry : filePathsByExtension.entrySet()) {
            String extension = entry.getKey();
            for (String filePath : entry.getValue()) {
                Optional<String> content = docStore.read(version, filePath);
                if (content.isEmpty()) {
                    continue;
                }
                tokenizeAndIndex(filePath, content.get(), wordOccurrences, minTokenLength, extension);
                totalFiles++;
            }
        }

        ContentIndex index = new ContentIndex(wordOccurrences);
        contentIndexStore.write(version, index);
        log.info("Content index built for version {}: {} unique words across {} files",
                version, wordOccurrences.size(), totalFiles);
        return index;
    }

    void tokenizeAndIndex(String filePath, String text, Map<String, List<ContentOccurrence>> wordOccurrences,
                          int minTokenLength, String extension) {
        int lineNumber = 1;
        int lineStart = 0;

        Matcher matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            // Update line number based on newlines between last lineStart and current match
            int matchStart = matcher.start();
            for (int i = lineStart; i < matchStart; i++) {
                if (text.charAt(i) == '\n') {
                    lineNumber++;
                }
            }
            lineStart = matchStart;

            String token = matcher.group().toLowerCase();
            if (token.length() < minTokenLength) {
                continue;
            }
            if (KeywordIndexer.WORD_INDEX_BLACK_LIST.contains(token)) {
                continue;
            }

            wordOccurrences.computeIfAbsent(token, k -> new ArrayList<>())
                    .add(new ContentOccurrence(filePath, matchStart, lineNumber, extension));
        }
    }
}
