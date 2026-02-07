package com.fvd.indexs.indexers;

import java.util.List;

public class FileKeywordEntry {

    public String path;
    public List<KeywordScore> keywords;
    public List<SectionKeywordEntry> sections;

    public FileKeywordEntry() {
    }

    public FileKeywordEntry(String path, List<KeywordScore> keywords, List<SectionKeywordEntry> sections) {
        this.path = path;
        this.keywords = keywords;
        this.sections = sections;
    }
}
