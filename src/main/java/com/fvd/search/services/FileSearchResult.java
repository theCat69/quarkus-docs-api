package com.fvd.search.services;

public class FileSearchResult {

    public String path;
    public double score;

    public FileSearchResult() {
    }

    public FileSearchResult(String path, double score) {
        this.path = path;
        this.score = score;
    }
}
