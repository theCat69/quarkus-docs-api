package com.fvd.search.services;

import com.fvd.common.Stemmer;
import com.fvd.search.KeywordScoringConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service for calculating hierarchical keyword scores.
 * Keywords are weighted based on their structural location within documents.
 *
 * Weight Hierarchy:
 * - filename: 10x (highest priority)
 * - title (H1): 8x
 * - section (H2): 5x
 * - subtitle (H3+): 2x
 * - body: 1x (base)
 *
 * Score formula: keyword_score = base_score * location_multiplier * frequency_factor
 * where frequency_factor = min(1.0 + log(count), 2.0)
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class KeywordScorer {

    /**
     * Source type constants for keyword locations.
     */
    public static final String SOURCE_FILENAME = "filename";
    public static final String SOURCE_TITLE = "title";
    public static final String SOURCE_SECTION = "section";
    public static final String SOURCE_SUBTITLE = "subtitle";
    public static final String SOURCE_BODY = "body";

    private static final Pattern FILENAME_SEPARATOR = Pattern.compile("[-_]");
    private static final Pattern NON_WORD = Pattern.compile("[^a-zA-Z0-9]");

    private final KeywordScoringConfig config;

    /**
     * Calculate weighted score for a keyword match.
     *
     * @param source the source location (filename, title, section, subtitle, body)
     * @param frequency the number of occurrences
     * @return the calculated weighted score
     */
    public double calculateScore(String source, int frequency) {
        double multiplier = getMultiplier(source);
        double frequencyFactor = calculateFrequencyFactor(frequency);
        return multiplier * frequencyFactor;
    }

    /**
     * Calculate weighted score with a base score.
     *
     * @param baseScore the base score for the keyword
     * @param source the source location
     * @param frequency the number of occurrences
     * @return the calculated weighted score
     */
    public double calculateScore(double baseScore, String source, int frequency) {
        double multiplier = getMultiplier(source);
        double frequencyFactor = calculateFrequencyFactor(frequency);
        return baseScore * multiplier * frequencyFactor;
    }

    /**
     * Calculate the frequency factor for a given count.
     * Formula: min(1.0 + log(count), 2.0)
     *
     * @param frequency the number of occurrences
     * @return the frequency factor (between 1.0 and 2.0)
     */
    public double calculateFrequencyFactor(int frequency) {
        if (frequency <= 0) {
            return 0.0;
        }
        return Math.min(1.0 + Math.log(frequency), 2.0);
    }

    /**
     * Get location multiplier for a source type.
     *
     * @param source the source type
     * @return the weight multiplier for that source
     */
    public double getMultiplier(String source) {
        if (source == null) {
            return config.bodyWeight();
        }
        return switch (source.toLowerCase()) {
            case SOURCE_FILENAME -> config.filenameWeight();
            case SOURCE_TITLE -> config.titleWeight();
            case SOURCE_SECTION -> config.sectionWeight();
            case SOURCE_SUBTITLE -> config.subtitleWeight();
            default -> config.bodyWeight();
        };
    }

    /**
     * Extract keywords from filename.
     * Removes extension, splits by hyphens/underscores, filters stopwords.
     *
     * @param filename the filename (can include path)
     * @return list of extracted keywords (unstemmed, lowercase)
     */
    public List<String> extractFilenameKeywords(String filename) {
        if (filename == null || filename.isBlank()) {
            return List.of();
        }

        // Extract just the filename from path
        String name = filename;
        int lastSlash = filename.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = filename.substring(lastSlash + 1);
        }

        // Remove file extension
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex);
        }

        // Split by hyphens and underscores
        String[] parts = FILENAME_SEPARATOR.split(name);
        Set<String> stopwords = Set.copyOf(config.filenameStopwords());

        List<String> keywords = new ArrayList<>();
        for (String part : parts) {
            // Clean and normalize
            String cleaned = NON_WORD.matcher(part).replaceAll("").toLowerCase().trim();
            if (cleaned.length() >= 2 && !stopwords.contains(cleaned)) {
                keywords.add(cleaned);
            }
        }

        return keywords;
    }

    /**
     * Extract stemmed keywords from filename.
     *
     * @param filename the filename (can include path)
     * @return list of stemmed keywords
     */
    public List<String> extractStemmedFilenameKeywords(String filename) {
        return extractFilenameKeywords(filename).stream()
                .map(Stemmer::stem)
                .toList();
    }

    /**
     * Determine the heading source type from AsciiDoc heading level.
     *
     * @param headingLevel the number of '=' characters (1-5)
     * @return the source type (title, section, or subtitle)
     */
    public String getSourceFromHeadingLevel(int headingLevel) {
        return switch (headingLevel) {
            case 1 -> SOURCE_TITLE;
            case 2 -> SOURCE_SECTION;
            default -> SOURCE_SUBTITLE;
        };
    }

    /**
     * Parse heading level from AsciiDoc heading line.
     * Returns 0 if not a heading.
     *
     * @param line the line to parse
     * @return the heading level (1-5) or 0 if not a heading
     */
    public int parseHeadingLevel(String line) {
        if (line == null || line.isBlank()) {
            return 0;
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith("=")) {
            return 0;
        }
        int level = 0;
        for (int i = 0; i < trimmed.length() && trimmed.charAt(i) == '='; i++) {
            level++;
        }
        // Must be followed by space and have content
        if (level <= trimmed.length() - 2 && trimmed.charAt(level) == ' ') {
            return level;
        }
        return 0;
    }

    /**
     * Get the highest-weight score when a keyword appears in multiple locations.
     * Uses the highest weight multiplier (filename > title > section > subtitle > body).
     *
     * @param sources list of source locations where the keyword appears
     * @return the highest multiplier among the sources
     */
    public double getHighestMultiplier(List<String> sources) {
        if (sources == null || sources.isEmpty()) {
            return config.bodyWeight();
        }
        return sources.stream()
                .mapToDouble(this::getMultiplier)
                .max()
                .orElse(config.bodyWeight());
    }

    /**
     * Combine scores from multiple sources for the same keyword.
     * Uses the highest weight source's score.
     *
     * @param scores list of (source, frequency) pairs for a keyword
     * @return the combined score using highest weight
     */
    public double combineScores(List<SourceFrequency> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0.0;
        }

        // Find highest-weight source and total frequency
        double highestMultiplier = 0.0;
        int totalFrequency = 0;
        String bestSource = SOURCE_BODY;

        for (SourceFrequency sf : scores) {
            double mult = getMultiplier(sf.source());
            if (mult > highestMultiplier) {
                highestMultiplier = mult;
                bestSource = sf.source();
            }
            totalFrequency += sf.frequency();
        }

        return calculateScore(bestSource, totalFrequency);
    }

    /**
     * Record representing a source and its keyword frequency.
     */
    public record SourceFrequency(String source, int frequency) {
    }

    /**
     * Record representing a weighted keyword with source information.
     */
    public record WeightedKeyword(
            String keyword,
            String stemmed,
            String source,
            double score,
            int frequency
    ) {
    }
}
