package com.fvd.search.services;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
public class SectionSearchResult {

    public String path;
    public String section;
    public int start;
    public int end;
    public double score;
    public List<MatchedKeyword> matchedKeywords;
    public String extension;
    public String snippet;
    public String matchedSectionTitle;
    public double sectionMatchScore;

    /**
     * Constructor without the new snippet/sectionTitle fields for backward compatibility.
     */
    public SectionSearchResult(String path, String section, int start, int end,
                               double score, List<MatchedKeyword> matchedKeywords, String extension) {
        this(path, section, start, end, score, matchedKeywords, extension, null, null, 0.0);
    }

}
