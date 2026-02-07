package com.fvd;

public class DocResponse {

    public String path;
    public String content;
    public String format;

    public DocResponse() {
    }

    public DocResponse(String path, String content, String format) {
        this.path = path;
        this.content = content;
        this.format = format;
    }
}
