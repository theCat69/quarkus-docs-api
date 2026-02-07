package com.fvd;

public class SectionSearchResult {

    public String path;
    public String section;
    public int start;
    public int end;
    public double score;

    public SectionSearchResult() {
    }

    public SectionSearchResult(String path, String section, int start, int end, double score) {
        this.path = path;
        this.section = section;
        this.start = start;
        this.end = end;
        this.score = score;
    }
}
