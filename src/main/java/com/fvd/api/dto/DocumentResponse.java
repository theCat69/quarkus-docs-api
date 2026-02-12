package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Full document response with structured content.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {

    public String title;
    public String description;
    public String path;
    public String subject;
    public String extension;
    public List<SectionInfo> sections;
    public List<CodeBlockInfo> codeBlocks;
    public List<String> matchedKeywords;
    public Double score;

}
