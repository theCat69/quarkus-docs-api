package com.fvd.common.matchers;

import com.fvd.search.SearchConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CDI bean for fuzzy string matching used in section title lookups.
 * Combines Levenshtein similarity, substring containment, and word overlap
 * to score candidates and select the best match above a configurable threshold.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class FuzzyMatcher {

    private static final Pattern NON_WORD = Pattern.compile("[^a-zA-Z0-9]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final SearchConfig searchConfig;

    /**
     * Result of a fuzzy match containing the matched value, score, and match type.
     */
    public record MatchResult(String value, double score, String matchType) {
    }

    /**
     * Computes the Levenshtein distance between two strings.
     */
    static int levenshteinDistance(String a, String b) {
        int lenA = a.length();
        int lenB = b.length();
        int[][] dp = new int[lenA + 1][lenB + 1];

        for (int i = 0; i <= lenA; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= lenB; j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= lenA; i++) {
            for (int j = 1; j <= lenB; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[lenA][lenB];
    }

    /**
     * Returns a similarity score (0.0-1.0) based on Levenshtein distance.
     * 1.0 means identical strings.
     */
    public double levenshteinSimilarity(String a, String b) {
        if (a == null || b == null) {
            return 0.0;
        }
        String la = a.toLowerCase();
        String lb = b.toLowerCase();
        if (la.equals(lb)) {
            return 1.0;
        }
        int maxLen = Math.max(la.length(), lb.length());
        if (maxLen == 0) {
            return 1.0;
        }
        int distance = levenshteinDistance(la, lb);
        return 1.0 - ((double) distance / maxLen);
    }

    /**
     * Returns a containment score (0.0-1.0).
     * 1.0 if one string fully contains the other; proportional otherwise based on length ratio.
     */
    public double containmentScore(String query, String candidate) {
        if (query == null || candidate == null) {
            return 0.0;
        }
        String lq = query.toLowerCase();
        String lc = candidate.toLowerCase();

        if (lc.contains(lq)) {
            return (double) lq.length() / lc.length();
        }
        if (lq.contains(lc)) {
            return (double) lc.length() / lq.length();
        }
        return 0.0;
    }

    /**
     * Returns a word overlap score (0.0-1.0) based on shared words between query and candidate.
     * Tokenizes both strings, counts shared words, divides by total unique words.
     */
    public double wordOverlapScore(String query, String candidate) {
        if (query == null || candidate == null) {
            return 0.0;
        }
        Set<String> queryWords = tokenizeToSet(query);
        Set<String> candidateWords = tokenizeToSet(candidate);

        if (queryWords.isEmpty() || candidateWords.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(queryWords);
        intersection.retainAll(candidateWords);

        Set<String> union = new HashSet<>(queryWords);
        union.addAll(candidateWords);

        if (union.isEmpty()) {
            return 0.0;
        }
        return (double) intersection.size() / union.size();
    }

    /**
     * Computes a combined fuzzy score from Levenshtein similarity, containment, and word overlap.
     * Weights are sourced from SearchConfig.
     */
    public double combinedScore(String query, String candidate) {
        double lev = levenshteinSimilarity(query, candidate);
        double cont = containmentScore(query, candidate);
        double overlap = wordOverlapScore(query, candidate);
        return searchConfig.fuzzy().levenshteinWeight() * lev
                + searchConfig.fuzzy().containmentWeight() * cont
                + searchConfig.fuzzy().wordOverlapWeight() * overlap;
    }

    /**
     * Finds the best fuzzy match from a list of candidates above the default threshold.
     *
     * @param query      the search query
     * @param candidates list of candidate strings to match against
     * @return the best match result, or empty if no candidate exceeds the threshold
     */
    public Optional<MatchResult> bestMatch(String query, List<String> candidates) {
        return bestMatch(query, candidates, searchConfig.fuzzy().defaultThreshold());
    }

    /**
     * Finds the best fuzzy match from a list of candidates above a given threshold.
     *
     * @param query      the search query
     * @param candidates list of candidate strings to match against
     * @param threshold  minimum combined score to accept (0.0-1.0)
     * @return the best match result, or empty if no candidate exceeds the threshold
     */
    public Optional<MatchResult> bestMatch(String query, List<String> candidates, double threshold) {
        if (query == null || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        String lowerQuery = query.toLowerCase();
        MatchResult best = null;

        for (String candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String lowerCandidate = candidate.toLowerCase();

            // Check exact match first
            if (lowerCandidate.equals(lowerQuery)) {
                return Optional.of(new MatchResult(candidate, 1.0, "exact"));
            }

            double score = combinedScore(query, candidate);
            String matchType = determineMatchType(query, candidate, score);

            if (score >= threshold && (best == null || score > best.score())) {
                best = new MatchResult(candidate, score, matchType);
            }
        }

        return Optional.ofNullable(best);
    }

    private String determineMatchType(String query, String candidate, double score) {
        double cont = containmentScore(query, candidate);
        double overlap = wordOverlapScore(query, candidate);

        if (cont > searchConfig.fuzzy().containmentPartialThreshold()) {
            return "partial";
        }
        if (overlap > searchConfig.fuzzy().wordOverlapKeywordThreshold()) {
            return "keyword";
        }
        return "partial";
    }

    private static Set<String> tokenizeToSet(String text) {
        String cleaned = NON_WORD.matcher(text.toLowerCase()).replaceAll(" ");
        String[] words = WHITESPACE.split(cleaned.trim());
        Set<String> result = new HashSet<>();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.add(word);
            }
        }
        return result;
    }
}
