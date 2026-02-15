package com.fvd.indexs.indexers;

import com.fvd.asciidocs.model.DocumentMetadata;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
public class FileKeywordEntry {

    public String path;
    public List<KeywordScore> keywords;
    public List<SectionKeywordEntry> sections;
    public String extension;
    public DocumentMetadata metadata;

    /**
     * Constructor without extension and metadata for backward compatibility.
     */
    public FileKeywordEntry(String path, List<KeywordScore> keywords, List<SectionKeywordEntry> sections) {
        this(path, keywords, sections, "quarkus-core", null);
    }

    /**
     * Constructor without metadata for backward compatibility.
     */
    public FileKeywordEntry(String path, List<KeywordScore> keywords, List<SectionKeywordEntry> sections, String extension) {
        this(path, keywords, sections, extension, null);
    }

}
