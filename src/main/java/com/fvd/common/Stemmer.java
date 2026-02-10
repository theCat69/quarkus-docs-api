package com.fvd.common;

/**
 * Simple English suffix-stripping stemmer for search indexing.
 * Applies suffix rules in order (first match wins), then reduces
 * trailing duplicate consonants (e.g., "runn" → "run").
 * <p>
 * Intentionally simple and deterministic — produces consistent
 * grouping rather than linguistically perfect stems.
 */
public final class Stemmer {

    private static final String VOWELS = "aeiou";

    private static final String[][] SUFFIX_RULES = {
            {"ation", ""},   // configuration → configur
            {"tion", ""},    // action → act
            {"sion", ""},    // expression → expres
            {"ment", ""},    // management → manage
            {"ness", ""},    // darkness → dark
            {"able", ""},    // configurable → configur
            {"ible", ""},    // accessible → access
            {"ous", ""},     // dangerous → danger
            {"ive", ""},     // active → act
            {"ity", ""},     // security → secur
            {"ful", ""},     // powerful → power
            {"less", ""},    // powerless → power
    };

    // Suffixes that require remaining length >= 3 after stripping
    private static final String[][] MIN_LENGTH_SUFFIX_RULES = {
            {"ing", ""},     // running → runn → run (with dup reduction)
            {"ed", ""},      // configured → configur
            {"ly", ""},      // quickly → quick
            {"er", ""},      // runner → runn → run
            {"est", ""},     // fastest → fast
            {"es", ""},      // classes → class
    };

    // Special case: -s stripping (only if not ending in "ss" and remaining >= 3)
    private static final String S_SUFFIX = "s";

    private Stemmer() {
    }

    // Suffixes after which trailing duplicate consonant reduction should apply
    private static final java.util.Set<String> DUP_REDUCTION_SUFFIXES = java.util.Set.of("ing", "ed", "er");

    /**
     * Returns the stemmed form of a lowercase word.
     * Applies suffix stripping followed by trailing duplicate consonant reduction
     * (only when the stripped suffix commonly produces doubled consonants).
     *
     * @param word a lowercase word to stem
     * @return the stemmed form
     */
    public static String stem(String word) {
        if (word == null || word.length() < 3) {
            return word;
        }

        String[] result = applySuffixRules(word);
        String stemmed = result[0];
        String strippedSuffix = result[1];
        if (DUP_REDUCTION_SUFFIXES.contains(strippedSuffix)) {
            stemmed = reduceTrailingDuplicateConsonant(stemmed);
        }
        return stemmed;
    }

    /**
     * @return a two-element array: [stemmed word, stripped suffix (or empty string if none)]
     */
    private static String[] applySuffixRules(String word) {
        // First try the basic suffix rules (no minimum remaining length constraint beyond 1)
        for (String[] rule : SUFFIX_RULES) {
            String suffix = rule[0];
            if (word.endsWith(suffix)) {
                String remaining = word.substring(0, word.length() - suffix.length());
                if (remaining.length() >= 3) {
                    return new String[]{remaining, suffix};
                }
                // If remaining is too short, don't strip — fall through to next rule
            }
        }

        // Then try suffixes that require remaining length >= 3
        for (String[] rule : MIN_LENGTH_SUFFIX_RULES) {
            String suffix = rule[0];
            if (word.endsWith(suffix)) {
                String remaining = word.substring(0, word.length() - suffix.length());
                if (remaining.length() >= 3) {
                    return new String[]{remaining, suffix};
                }
                // If remaining is too short, don't strip — fall through
            }
        }

        // Special case: -s (only if not ending in "ss" and remaining >= 3)
        if (word.endsWith(S_SUFFIX) && !word.endsWith("ss")) {
            String remaining = word.substring(0, word.length() - 1);
            if (remaining.length() >= 3) {
                return new String[]{remaining, S_SUFFIX};
            }
        }

        return new String[]{word, ""};
    }

    private static String reduceTrailingDuplicateConsonant(String word) {
        if (word.length() < 2) {
            return word;
        }
        char last = word.charAt(word.length() - 1);
        char secondLast = word.charAt(word.length() - 2);
        if (last == secondLast && !isVowel(last)) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    private static boolean isVowel(char c) {
        return VOWELS.indexOf(c) >= 0;
    }
}
