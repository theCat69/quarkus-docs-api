package com.fvd.indexs.indexers;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
public class SectionKeywordEntry {

    public String title;
    public int start;
    public int end;
    public List<KeywordScore> keywords;

}
