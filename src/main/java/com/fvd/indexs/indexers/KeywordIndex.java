package com.fvd.indexs.indexers;

import java.util.List;

public class KeywordIndex {

    public List<FileKeywordEntry> files;

    public KeywordIndex() {
    }

    public KeywordIndex(List<FileKeywordEntry> files) {
        this.files = files;
    }
}
