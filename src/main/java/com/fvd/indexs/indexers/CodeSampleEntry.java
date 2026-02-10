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
    public String extension;

    /**
     * Constructor without extension for backward compatibility.
     */
    public CodeSampleEntry(String filePath, String sectionTitle, String language,
                           String content, int startLine, int endLine, List<KeywordScore> keywords) {
        this(filePath, sectionTitle, language, content, startLine, endLine, keywords, "quarkus-core");
    }

}
