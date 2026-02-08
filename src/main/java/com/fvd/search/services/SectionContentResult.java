package com.fvd.search.services;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class SectionContentResult {

    public String path;
    public String title;
    public int startLine;
    public int endLine;
    public String content;
    public String matchedTitle;
    public double matchScore;
    public String matchType;

    /**
     * Convenience constructor for backward compatibility (exact match).
     */
    public SectionContentResult(String path, String title, int startLine, int endLine, String content) {
        this(path, title, startLine, endLine, content, title, 1.0, "exact");
    }

}
