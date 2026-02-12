package com.fvd.search.services;

import com.fvd.repository.domain.MatchedKeyword;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
public class CodeSampleSearchResult {

    public String path;
    public String sectionTitle;
    public String matchedSectionTitle;
    public double sectionMatchScore;
    public String language;
    public String content;
    public int startLine;
    public int endLine;
    public double score;
    public List<MatchedKeyword> matchedKeywords;
    public String extension;

}
