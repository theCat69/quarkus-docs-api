package com.fvd.search.services;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

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

}
