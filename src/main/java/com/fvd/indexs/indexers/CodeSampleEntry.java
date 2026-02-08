package com.fvd.indexs.indexers;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
public class CodeSampleEntry {

    public String filePath;
    public String sectionTitle;
    public String language;
    public String content;
    public int startLine;
    public int endLine;
    public List<KeywordScore> keywords;

}
