package com.fvd.indexs.indexers;

import com.fvd.search.services.KeywordScorer;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Represents a keyword with its score and source information.
 * Source indicates where the keyword was found (filename, title, section, subtitle, body).
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class KeywordScore {

    public String word;
    public int score;
    public String source;
    public int frequency;

    /**
     * Backward-compatible constructor without source information.
     */
    public KeywordScore(String word, int score) {
        this(word, score, KeywordScorer.SOURCE_BODY, 1);
    }
}
