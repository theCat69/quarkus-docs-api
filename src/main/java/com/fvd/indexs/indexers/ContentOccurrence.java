package com.fvd.indexs.indexers;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class ContentOccurrence {

    public String filePath;
    public int charOffset;
    public int lineNumber;
    public String extension;

    /**
     * Constructor without extension for backward compatibility.
     */
    public ContentOccurrence(String filePath, int charOffset, int lineNumber) {
        this(filePath, charOffset, lineNumber, "quarkus-core");
    }

}
