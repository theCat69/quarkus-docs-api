package com.fvd.api.dto;

import com.fasterxml.jackson.annotation.JsonFilter;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Code sample result with context and metadata.
 */
@JsonFilter("fieldSelector")
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class CodeSampleResult {

    public String language;
    public String content;
    public String context;
    public String documentPath;
    public String documentTitle;
    public String subject;
    public String extension;
    public List<String> matchedKeywords;
    public double score;
    public int startLine;
    public int endLine;

}
