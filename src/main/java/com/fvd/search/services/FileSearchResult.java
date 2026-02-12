package com.fvd.search.services;

import com.fvd.repository.domain.MatchedKeyword;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
public class FileSearchResult {

    public String path;
    public double score;
    public List<MatchedKeyword> matchedKeywords;
    public String extension;

}
