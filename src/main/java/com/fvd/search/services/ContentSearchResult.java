package com.fvd.search.services;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
public class ContentSearchResult {

    public String path;
    public String snippet;
    public int matchOffset;
    public int matchLine;
    public double score;
    public List<String> matchedKeywords;
    public int matchCount;
    public String extension;

}
