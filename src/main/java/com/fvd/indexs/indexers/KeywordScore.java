package com.fvd.indexs.indexers;

import com.fvd.search.services.KeywordScorer;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.NoArgsConstructor;

/**
 * Represents a keyword with its score and source information.
 * Source indicates where the keyword was found (filename, title, section, subtitle, body).
 * The {@code originalWord} field stores the un-stemmed form of the keyword for display purposes.
 */
@RegisterForReflection
@NoArgsConstructor
public class KeywordScore {

    public String word;
    public String originalWord;
    public int score;
    public String source;
    public int frequency;

    /**
     * Full constructor with all fields including originalWord.
     */
    public KeywordScore(String word, String originalWord, int score, String source, int frequency) {
        this.word = word;
        this.originalWord = originalWord;
        this.score = score;
        this.source = source;
        this.frequency = frequency;
    }

    /**
     * Backward-compatible constructor without originalWord.
     * Falls back to using the stemmed word as the original.
     */
    public KeywordScore(String word, int score, String source, int frequency) {
        this(word, word, score, source, frequency);
    }

    /**
     * Backward-compatible constructor without source information.
     */
    public KeywordScore(String word, int score) {
        this(word, word, score, KeywordScorer.SOURCE_BODY, 1);
    }
}
