package com.fvd.indexs.indexers;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
public class FileKeywordEntry {

    public String path;
    public List<KeywordScore> keywords;
    public List<SectionKeywordEntry> sections;
    public String extension;

    /**
     * Constructor without extension for backward compatibility.
     */
    public FileKeywordEntry(String path, List<KeywordScore> keywords, List<SectionKeywordEntry> sections) {
        this(path, keywords, sections, "quarkus-core");
    }

}
