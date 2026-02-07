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

}
