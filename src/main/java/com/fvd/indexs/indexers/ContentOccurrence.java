package com.fvd.indexs.indexers;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class ContentOccurrence {

    public String filePath;
    public int charOffset;
    public int lineNumber;

}
