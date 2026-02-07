package com.fvd.search.services;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class SectionSearchResult {

    public String path;
    public String section;
    public int start;
    public int end;
    public double score;

}
