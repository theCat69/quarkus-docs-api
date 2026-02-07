package com.fvd.indexs.indexers;

import java.util.List;

public class SectionKeywordEntry {

    public String title;
    public int start;
    public int end;
    public List<KeywordScore> keywords;

    public SectionKeywordEntry() {
    }

    public SectionKeywordEntry(String title, int start, int end, List<KeywordScore> keywords) {
        this.title = title;
        this.start = start;
        this.end = end;
        this.keywords = keywords;
    }
}
