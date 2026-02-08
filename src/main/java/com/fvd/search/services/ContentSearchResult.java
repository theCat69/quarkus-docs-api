package com.fvd.search.services;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class ContentSearchResult {

    public String path;
    public String snippet;
    public int matchOffset;
    public int matchLine;
    public double score;

}
