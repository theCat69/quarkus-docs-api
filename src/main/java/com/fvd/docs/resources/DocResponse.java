package com.fvd.docs.resources;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class DocResponse {

    public String path;
    public String content;
    public String format;
    public String extension;

    /**
     * Constructor without extension for backward compatibility.
     */
    public DocResponse(String path, String content, String format) {
        this(path, content, format, "quarkus-core");
    }

}
